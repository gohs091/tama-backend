package com.tamagochi.tamagochi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 관리자 키 설정 여부에 따라 관리자 엔드포인트가 만들어지는지, 로그인 토큰 검사와 따로 동작하는지 앱 전체로 확인한다. */
class AdminEndpointWiringTest {

    private static final String BODY = "{\"tossUserId\":\"toss_x\"}";

    @Nested
    @SpringBootTest(properties = "tamagotchi.admin.key=wiring-test-key")
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @DisplayName("관리자 키를 설정한 경우")
    class WithKey {
        @Autowired MockMvc mvc;

        @Test
        @DisplayName("엔드포인트가 있고, 로그인 토큰이 아니라 관리자 키로 보호된다 (키가 없으면 401이 아니라 403)")
        void protectedByAdminKey() throws Exception {
            mvc.perform(post("/api/admin/promotion/test-grant").contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/admin/promotion/test-grant").header("X-Admin-Key", "wrong")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("관리자 키가 맞아도 TEST_ 코드가 아니면(여기선 코드 없음) 거절한다")
        void rejectedWithoutTestCode() throws Exception {
            mvc.perform(post("/api/admin/promotion/test-grant").header("X-Admin-Key", "wiring-test-key")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @DisplayName("관리자 키를 설정하지 않은 경우")
    class WithoutKey {
        @Autowired MockMvc mvc;

        @Test
        @DisplayName("엔드포인트가 만들어지지 않는다 (404)")
        void endpointDoesNotExist() throws Exception {
            mvc.perform(post("/api/admin/promotion/test-grant").header("X-Admin-Key", "anything")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isNotFound());
        }
    }
}
