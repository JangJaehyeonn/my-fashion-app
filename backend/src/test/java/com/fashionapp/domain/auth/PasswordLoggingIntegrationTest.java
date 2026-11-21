package com.fashionapp.domain.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 가입·로그인 실패/성공·잠금·규칙 위반·비밀번호 변경을 모두 거치는 동안 콘솔(로그)에
 * 평문 비밀번호도 BCrypt 해시도 찍히지 않는지 확인.
 */
@ExtendWith(OutputCaptureExtension.class)
class PasswordLoggingIntegrationTest extends LocalAuthTestSupport {

    private static final String SIGNUP_PASSWORD = "Leak1Check9";
    private static final String WRONG_PASSWORD = "Wrong7Guess3";
    private static final String NEW_PASSWORD = "Next5Secret2";

    @Test
    @DisplayName("비밀번호·해시가 로그에 남지 않는다")
    void passwordsAndHashesNeverLogged(CapturedOutput output) throws Exception {
        String loginId = newLoginId();
        signup(loginId, SIGNUP_PASSWORD, "로그확인");
        signup(newLoginId(), "bad", "규칙위반");               // INVALID_PASSWORD 경로
        for (int i = 1; i <= 5; i++) login(loginId, WRONG_PASSWORD); // 실패 + 잠금 경로
        redisTemplate.delete(LoginAttemptLimiter.lockKey(loginId));
        String body = login(loginId, SIGNUP_PASSWORD);
        String access = com.jayway.jsonpath.JsonPath.read(body, "$.data.accessToken");
        mockMvc.perform(patch("/api/users/me/password").header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("currentPassword", WRONG_PASSWORD, "newPassword", NEW_PASSWORD)));   // 현재 비밀번호 오류 경로
        mockMvc.perform(patch("/api/users/me/password").header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("currentPassword", SIGNUP_PASSWORD, "newPassword", NEW_PASSWORD)));

        String hash = userRepository.findByLoginId(loginId).orElseThrow().getPasswordHash();
        String logs = output.getAll();
        assertThat(logs).contains("local login failed loginId=" + loginId); // 로그 캡처가 실제로 동작하는지
        assertThat(logs)
                .doesNotContain(SIGNUP_PASSWORD)
                .doesNotContain(WRONG_PASSWORD)
                .doesNotContain(NEW_PASSWORD)
                .doesNotContain(hash)
                .doesNotContain("$2a$");
    }

    private String login(String loginId, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("loginId", loginId, "password", password)))
                .andReturn().getResponse().getContentAsString();
    }
}
