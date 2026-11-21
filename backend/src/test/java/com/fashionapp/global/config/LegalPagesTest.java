package com.fashionapp.global.config;

import com.fashionapp.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /privacy, /account-deletion: 로그인 없이 UTF-8 HTML로 열리고, 다른 경로는 여전히 인증이 필요 */
class LegalPagesTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("처리방침·계정 삭제 안내는 토큰 없이 200, text/html;charset=UTF-8")
    void pagesOpenWithoutAuth() throws Exception {
        mockMvc.perform(get("/privacy"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/html;charset=UTF-8"))
                .andExpect(content().string(containsString("개인정보처리방침")))
                .andExpect(content().string(containsString("OpenWeatherMap")));
        mockMvc.perform(get("/account-deletion"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/html;charset=UTF-8"))
                .andExpect(content().string(containsString("회원 탈퇴")));
    }

    @Test
    @DisplayName("원본 경로(/legal/**)는 노출되지 않고 /api 경로는 여전히 401")
    void otherPathsStillProtected() throws Exception {
        mockMvc.perform(get("/legal/privacy.html")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/images/clothes/x/y.jpg")).andExpect(status().isUnauthorized());
    }
}
