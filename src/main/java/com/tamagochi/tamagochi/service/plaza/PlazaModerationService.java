package com.tamagochi.tamagochi.service.plaza;

import com.tamagochi.tamagochi.domain.plaza.PlazaBlock;
import com.tamagochi.tamagochi.domain.plaza.PlazaReport;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.plaza.PlazaBlockRepository;
import com.tamagochi.tamagochi.repository.plaza.PlazaMuteRepository;
import com.tamagochi.tamagochi.repository.plaza.PlazaReportRepository;
import com.tamagochi.tamagochi.repository.user.UserRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 광장 차단 · 신고 저장. 광장 서비스는 잠금 하나로 도는 메모리 서비스라 DB 를 부르지 않으므로,
 * 저장은 스레드 하나짜리 실행기에서 한다 (PlazaHuntRunner 와 같은 방식). 한 스레드라 같은 사람의 차단 · 해제가 순서대로 쌓인다.
 * 접속할 때 차단 목록과 말 못 하게 된 때를 읽는 일은 연결을 받는 스레드가 직접 부른다 (광장 잠금 밖).
 */
@Slf4j
@Service
public class PlazaModerationService implements PlazaService.Moderation {

    private final PlazaService plazaService;
    private final UserRepository userRepository;
    private final PlazaBlockRepository blockRepository;
    private final PlazaReportRepository reportRepository;
    private final PlazaMuteRepository muteRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "plaza-moderation");
        t.setDaemon(true);
        return t;
    });

    public PlazaModerationService(PlazaService plazaService, UserRepository userRepository, PlazaBlockRepository blockRepository,
                                  PlazaReportRepository reportRepository, PlazaMuteRepository muteRepository) {
        this.plazaService = plazaService;
        this.userRepository = userRepository;
        this.blockRepository = blockRepository;
        this.reportRepository = reportRepository;
        this.muteRepository = muteRepository;
    }

    /** 앱이 시작할 때 광장에 저장 쪽을 건다 */
    @PostConstruct
    void install() {
        plazaService.setModeration(this);
    }

    @PreDestroy
    void stop() {
        executor.shutdown();
    }

    /** 광장 잠금 안에서 불리므로 일을 넣고 바로 돌아온다. 예외는 로그만 남긴다 */
    @Override
    public void block(long userId, String targetCode, boolean on) {
        executor.execute(() -> {
            try {
                saveBlock(userId, targetCode, on);
            } catch (Exception e) {
                log.warn("광장 차단 저장 실패 - user: {}, on: {}", userId, on, e);
            }
        });
    }

    @Override
    public void report(long reporterId, String targetCode, String why, List<String> lines, String roomKey) {
        executor.execute(() -> {
            try {
                saveReport(reporterId, targetCode, why, lines, roomKey);
            } catch (Exception e) {
                log.warn("광장 신고 저장 실패 - reporter: {}", reporterId, e);
            }
        });
    }

    /** 차단을 저장하거나 푼다. 같은 차단은 하나만 남고, 모르는 유저 코드나 자기 자신은 조용히 무시한다 */
    public void saveBlock(long userId, String targetCode, boolean on) {
        User target = targetCode == null || targetCode.isBlank() ? null : userRepository.findByUserCode(targetCode).orElse(null);
        if (target == null || target.getId().equals(userId)) return;
        if (!on) {
            blockRepository.deleteByUserIdAndBlockedUserId(userId, target.getId());
            return;
        }
        if (blockRepository.existsByUserIdAndBlockedUserId(userId, target.getId())) return;
        try {
            blockRepository.saveAndFlush(new PlazaBlock(userId, target.getId()));
        } catch (DataIntegrityViolationException e) {
            // 다른 곳에서 먼저 넣었다: 이미 차단돼 있으니 됐다
        }
    }

    @Transactional
    public void saveReport(long reporterId, String targetCode, String why, List<String> lines, String roomKey) {
        User target = targetCode == null || targetCode.isBlank() ? null : userRepository.findByUserCode(targetCode).orElse(null);
        if (target == null || target.getId().equals(reporterId)) return;
        reportRepository.save(new PlazaReport(reporterId, target.getId(), why, String.join("\n", lines), roomKey));
    }

    /** 이 사람이 차단한 사람들의 유저 코드 */
    @Transactional(readOnly = true)
    public Set<String> blockedCodes(long userId) {
        List<Long> ids = blockRepository.findByUserId(userId).stream().map(PlazaBlock::getBlockedUserId).toList();
        return userRepository.findAllById(ids).stream().map(User::getUserCode).filter(c -> c != null).collect(Collectors.toSet());
    }

    /** 이 사람이 말을 못 하게 된 때(epoch ms). 해당이 없으면 0 */
    @Transactional(readOnly = true)
    public long mutedUntil(long userId) {
        return muteRepository.findById(userId).map(m -> m.getUntil().toEpochMilli()).orElse(0L);
    }
}
