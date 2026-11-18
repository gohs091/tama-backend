package com.tamagochi.tamagochi.controller.gear;

import com.tamagochi.tamagochi.TowerLunchTestSupport;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.service.auth.AuthTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 기본 무기 API: 목록·구매·장착, 전투력 숫자 없음, 예전 탑 전투 카탈로그는 그대로 */
class GearApiTest extends TowerLunchTestSupport {

    @Autowired MockMvc mvc;
    @Autowired AuthTokenService tokens;

    private ResultActions call(String bearer, String path, String body) throws Exception {
        return mvc.perform(post(path).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    @DisplayName("GET /api/gear: 200, 기본 무기 두 종·맨손 피해·열매. 토큰이 없으면 401. 전투력 숫자는 없다")
    void getCatalog() throws Exception {
        User user = newUser(10, 100);
        String id = user.getTossUserId();
        String bearer = "Bearer " + tokens.issue(id);

        ResultActions res = mvc.perform(get("/api/gear/" + id).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provisional").value(true))
                .andExpect(jsonPath("$.note").isNotEmpty())
                .andExpect(jsonPath("$.catalog.length()").value(2))
                .andExpect(jsonPath("$.catalog[0].id").value("wooden-brush"))
                .andExpect(jsonPath("$.catalog[0].name").value("나무 붓"))
                .andExpect(jsonPath("$.catalog[0].slot").value("WEAPON"))
                .andExpect(jsonPath("$.catalog[0].style").value("MELEE"))
                .andExpect(jsonPath("$.catalog[0].tier").value("T1"))
                .andExpect(jsonPath("$.catalog[0].fruitPrice").value(10))
                .andExpect(jsonPath("$.catalog[0].meleeDamage").value(25))
                .andExpect(jsonPath("$.catalog[0].rangedDamage").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.catalog[0].owned").value(false))
                .andExpect(jsonPath("$.catalog[0].equipped").value(false))
                .andExpect(jsonPath("$.catalog[0].canBuy").value(true))
                .andExpect(jsonPath("$.catalog[0].unlockNpc").value("ns-woodpecker"))
                .andExpect(jsonPath("$.catalog[1].id").value("feather-nib"))
                .andExpect(jsonPath("$.catalog[1].name").value("깃 펜촉"))
                .andExpect(jsonPath("$.catalog[1].style").value("RANGED"))
                .andExpect(jsonPath("$.catalog[1].fruitPrice").value(10))
                .andExpect(jsonPath("$.catalog[1].rangedDamage").value(11))
                .andExpect(jsonPath("$.owned").isEmpty())
                .andExpect(jsonPath("$.equippedWeaponId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.attacks.meleeDamage").value(18))
                .andExpect(jsonPath("$.attacks.rangedDamage").value(8))
                .andExpect(jsonPath("$.fruit").value(10));

        String body = res.andReturn().getResponse().getContentAsString();
        String lower = body.toLowerCase();
        assertThat(lower).doesNotContain("\"bp\"").doesNotContain("battlepower").doesNotContain("recommendedbp")
                .doesNotContain("combatpower");
        res.andExpect(jsonPath("$..bp").isEmpty())
                .andExpect(jsonPath("$..recommendedBp").isEmpty())
                .andExpect(jsonPath("$..battlePower").isEmpty());

        mvc.perform(get("/api/gear/" + id)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("사서 끼고 벗는 흐름: 열매가 빠지고 피해가 바뀌고, 열매가 모자라면 거절한다")
    void buyEquipUnequip() throws Exception {
        User user = newUser(15, 100);
        String id = user.getTossUserId();
        String bearer = "Bearer " + tokens.issue(id);
        String buyBrush = "{\"tossUserId\":\"" + id + "\",\"id\":\"wooden-brush\"}";
        String buyNib = "{\"tossUserId\":\"" + id + "\",\"id\":\"feather-nib\"}";
        String unequip = "{\"tossUserId\":\"" + id + "\"}";

        call(bearer, "/api/gear/buy", buyBrush).andExpect(status().isOk())
                .andExpect(jsonPath("$.fruit").value(5))
                .andExpect(jsonPath("$.owned[0]").value("wooden-brush"))
                .andExpect(jsonPath("$.equippedWeaponId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.attacks.meleeDamage").value(18));

        call(bearer, "/api/gear/equip", buyBrush).andExpect(status().isOk())
                .andExpect(jsonPath("$.equippedWeaponId").value("wooden-brush"))
                .andExpect(jsonPath("$.catalog[0].equipped").value(true))
                .andExpect(jsonPath("$.attacks.meleeDamage").value(25))
                .andExpect(jsonPath("$.attacks.rangedDamage").value(8))
                .andExpect(jsonPath("$.fruit").value(5));

        call(bearer, "/api/gear/buy", buyNib).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_FRUIT"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        call(bearer, "/api/gear/equip", unequip).andExpect(status().isOk())
                .andExpect(jsonPath("$.equippedWeaponId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.attacks.meleeDamage").value(18))
                .andExpect(jsonPath("$.attacks.rangedDamage").value(8))
                .andExpect(jsonPath("$.owned[0]").value("wooden-brush"));

        call(bearer, "/api/gear/buy", buyBrush).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ALREADY_OWNED"));
        call(bearer, "/api/gear/equip", buyNib).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_OWNED"));
    }

    @Test
    @DisplayName("무기를 사도 GET /api/tower/combat 는 그대로다 (누구에게나 melee 25·ranged 11, 사람마다 다르지 않다)")
    void combatCatalogUnchanged() throws Exception {
        User armed = newUser(20, 100);
        User other = newUser(0, 100);
        String bearer = "Bearer " + tokens.issue(armed.getTossUserId());
        call(bearer, "/api/gear/buy", "{\"tossUserId\":\"" + armed.getTossUserId() + "\",\"id\":\"wooden-brush\"}")
                .andExpect(status().isOk());
        call(bearer, "/api/gear/equip", "{\"tossUserId\":\"" + armed.getTossUserId() + "\",\"id\":\"wooden-brush\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attacks.meleeDamage").value(25));

        String combatArmed = mvc.perform(get("/api/tower/combat").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.player.melee.damage").value(25))
                .andExpect(jsonPath("$.player.ranged.damage").value(11))
                .andExpect(jsonPath("$.player.hp").value(480))
                .andReturn().getResponse().getContentAsString();
        String combatOther = mvc.perform(get("/api/tower/combat")
                        .header("Authorization", "Bearer " + tokens.issue(other.getTossUserId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(combatOther).isEqualTo(combatArmed);
    }
}
