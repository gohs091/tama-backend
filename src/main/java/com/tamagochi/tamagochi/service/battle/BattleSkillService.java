package com.tamagochi.tamagochi.service.battle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tamagochi.tamagochi.domain.battle.BattleSkill;
import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.service.storage.PetImageUrls;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 끄적이 배틀 기술 정하기. 단계마다 한 번, 그 단계 그림을 OpenAI 비전 모델(gpt-4.1)에 보여 주고
 * 앱 연출 틀 18가지 중 가장 어울리는 틀·색 3개·입자·이름을 받는다. 진화하면 새 모습으로 다시 정한다.
 * 실패하면 빈 값: 앱은 기본 기술로 연출하고 다음에 다시 요청한다.
 */
@Service
@Slf4j
public class BattleSkillService {

    static final String PROMPT = """
            너는 다마고치 같은 육성 앱 '끄적이'의 기술 디자이너다. 유저가 그린 그림이 도트 캐릭터로 살아난 것이 끄적이다.
            이 캐릭터 그림을 보고 이 캐릭터만의 배틀 기술 하나를 정한다. 스파링에서 서로 주고받는 연출용이다(승패와 무관).
            template은 아래 18가지 중 이 캐릭터의 몸·무기·정체에 가장 잘 맞는 하나를 고른다. 늘 무난한 projectile만 고르지 말고 캐릭터에 딱 맞는 것을 고른다.
            - dash: 몸통 돌진 (멧돼지·코뿔소·자동차·돌고래)
            - projectile: 물체를 던지거나 쏨 (다람쥐 도토리·원숭이·대포)
            - slash: 발톱·칼로 베기 (호랑이·사마귀·닌자·검사)
            - barrage: 여러 발 연사 (탱크·전투기·딱따구리·씨앗)
            - vortex: 회오리·바람 (새·부엉이·봉황·나비·날개)
            - magic: 발밑 마법진에서 빛기둥 (마법사·요정·산신령)
            - rain: 머리 위 구름에서 쏟아짐 (고래·사탕·눈·비)
            - shockwave: 뛰었다 쿵, 땅을 타고 가는 충격파 (곰·코끼리·골렘·거인)
            - beam: 입이나 손에서 광선 (용·로봇·레이저)
            - bind: 촉수·덩굴로 휘감기 (문어·뱀·식물)
            - cloud: 먹물·안개·독가스 (오징어·유령·스컹크)
            - chomp: 큰 이빨로 콱 물기 (상어·악어·공룡)
            - spikes: 땅에서 가시가 솟음 (고슴도치·선인장·성게·수정)
            - burst: 폭탄을 던져 펑 (복어·슬라임·폭탄·불꽃)
            - soundwave: 소리·울음 파동 (늑대·박쥐·가수·스피커)
            - meteor: 하늘에서 불붙은 별·바위 낙하 (별·혜성·불사조·우주)
            - charm: 하트를 날려 홀리기 (여우·고양이·아이돌·하트)
            - lightning: 먹구름에서 낙뢰 (번개·전기·천둥새)
            규칙:
            - name: 한국어 기술 이름 6~12자. 귀엽고 살짝 웃기면서 그림의 특징(색·부위·정체)에서 나와야 한다. 예: "솜사탕 몸통 박치기", "불씨 꼬리 휘두르기"
            - colors: 그림의 색에서 뽑은 hex 3개 [주색, 진한 보조색, 아주 밝은 강조색]
            - particle: circle | square | spark | star | drop | leaf | feather | smoke | heart 중 그림에 어울리는 하나
            JSON만 출력: {"template":"...","name":"...","colors":["#..","#..","#.."],"particle":"..."}
            """;

    private final ObjectMapper objectMapper;
    private final TamagotchiRepository tamagotchiRepository;
    private final TransactionTemplate tx;
    private final WebClient openAiClient = WebClient.builder().baseUrl("https://api.openai.com")
            .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024)).build();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Value("${openai.api-key:}")
    private String openAiApiKey;

    @Value("${openai.vision-model:gpt-4.1}")
    private String visionModel;

    public BattleSkillService(ObjectMapper objectMapper, TamagotchiRepository tamagotchiRepository, TransactionTemplate tx) {
        this.objectMapper = objectMapper;
        this.tamagotchiRepository = tamagotchiRepository;
        this.tx = tx;
    }

    /** 저장된 기술 (지금 단계 것만). 없으면 빈 값 */
    public static Optional<BattleSkill> current(Tamagotchi t) {
        if (!t.hasSkillForCurrentStage()) return Optional.empty();
        return Optional.of(new BattleSkill(t.getSkillTemplate(), t.getSkillName(), BattleSkill.parseColors(t.getSkillColors()), t.getSkillParticle()));
    }

    /**
     * 지금 단계 기술이 없으면 정해서 저장한다. 그림은 앱이 보낸 것(data URL 또는 base64)을 쓰고, 없으면 서버에 올라간 지금 단계 그림을 받는다.
     * 알이거나 그림을 구할 수 없거나 AI가 실패하면 빈 값.
     */
    public Optional<BattleSkill> ensure(Long petId, String imageBase64) {
        Tamagotchi pet = tamagotchiRepository.findById(petId).orElse(null);
        if (pet == null || pet.getEvolutionStage() == EvolutionStage.EGG) return Optional.empty();
        Optional<BattleSkill> have = current(pet);
        if (have.isPresent()) return have;

        byte[] image = decode(imageBase64);
        if (image == null) image = download(PetImageUrls.of(pet.getCurrentImageKey()));
        if (image == null) return Optional.empty();

        Optional<BattleSkill> made = ask(image);
        if (made.isEmpty()) return Optional.empty();
        BattleSkill skill = made.get();
        String stageAtAsk = pet.getEvolutionStage().name();
        return tx.execute(status -> {
            Tamagotchi fresh = tamagotchiRepository.findById(petId).orElse(null);
            if (fresh == null) return Optional.<BattleSkill>empty();
            if (fresh.hasSkillForCurrentStage()) return current(fresh); // 동시에 두 번 요청됐으면 먼저 저장된 것
            if (!fresh.getEvolutionStage().name().equals(stageAtAsk)) return Optional.<BattleSkill>empty(); // 그사이 진화했다
            fresh.applySkill(skill.template(), skill.name(), skill.colorsCsv(), skill.particle());
            tamagotchiRepository.save(fresh);
            log.info("배틀 기술 정함 - pet={}, stage={}, template={}, name={}", petId, stageAtAsk, skill.template(), skill.name());
            return Optional.of(skill);
        });
    }

    Optional<BattleSkill> ask(byte[] png) {
        if (openAiApiKey == null || openAiApiKey.isBlank()) return Optional.empty();
        try {
            String mime = png.length > 2 && (png[0] & 0xFF) == 0xFF && (png[1] & 0xFF) == 0xD8 ? "image/jpeg" : "image/png";
            String dataUrl = "data:" + mime + ";base64," + java.util.Base64.getEncoder().encodeToString(png);
            Map<String, Object> body = Map.of(
                    "model", visionModel,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(Map.of("role", "user", "content", List.of(
                            Map.of("type", "text", "text", PROMPT),
                            Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))))));
            String json = openAiClient.post().uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve().bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            String content = objectMapper.readTree(json).path("choices").path(0).path("message").path("content").asText("");
            return Optional.of(parse(objectMapper.readTree(content)));
        } catch (Exception e) {
            log.warn("배틀 기술 정하기 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    static BattleSkill parse(JsonNode n) {
        List<String> colors = new ArrayList<>();
        n.path("colors").forEach(c -> colors.add(c.asText()));
        return BattleSkill.sanitize(n.path("template").asText(null), n.path("name").asText(null), colors, n.path("particle").asText(null));
    }

    private static byte[] decode(String b64) {
        if (b64 == null || b64.isBlank()) return null;
        String raw = b64.contains(",") ? b64.substring(b64.indexOf(',') + 1) : b64;
        if (raw.length() > 3_000_000) return null; // 끄적이 그림은 수백 KB. 너무 크면 받지 않는다
        try {
            return java.util.Base64.getDecoder().decode(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static byte[] download(String url) {
        if (url == null) return null;
        try {
            HttpResponse<byte[]> res = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            return res.statusCode() == 200 ? res.body() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
