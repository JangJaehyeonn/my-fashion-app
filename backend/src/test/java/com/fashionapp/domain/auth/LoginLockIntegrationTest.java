package com.fashionapp.domain.auth;

import com.fashionapp.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 로그인 연속 5회 실패 → 5분 잠금 (Redis) */
class LoginLockIntegrationTest extends LocalAuthTestSupport {

    @Test
    @DisplayName("4번째 실패까지 401, 5번째 실패에 429 잠금, 잠금 중엔 맞는 비밀번호도 429, 잠금은 5분(300초) TTL")
    void locksAfterFiveFailures() throws Exception {
        String loginId = newLoginId();
        signupOk(loginId);

        for (int i = 1; i <= 4; i++) {
            login(loginId, "wrong12345").andExpect(status().isUnauthorized());
        }
        login(loginId, "wrong12345")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(ErrorCode.LOGIN_LOCKED.getMessage()));

        login(loginId, PASSWORD).andExpect(status().isTooManyRequests());

        Long ttl = redisTemplate.getExpire(LoginAttemptLimiter.lockKey(loginId));
        assertThat(ttl).isBetween(290L, 300L);
    }

    @Test
    @DisplayName("잠금이 풀리면(만료 흉내: 키 삭제) 맞는 비밀번호로 로그인된다")
    void unlocksAfterExpiry() throws Exception {
        String loginId = newLoginId();
        signupOk(loginId);
        for (int i = 1; i <= 5; i++) login(loginId, "wrong12345");
        login(loginId, PASSWORD).andExpect(status().isTooManyRequests());

        redisTemplate.delete(LoginAttemptLimiter.lockKey(loginId));

        login(loginId, PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("성공하면 실패 횟수가 초기화된다 (연속 실패만 셈)")
    void successResetsCounter() throws Exception {
        String loginId = newLoginId();
        signupOk(loginId);

        for (int i = 1; i <= 3; i++) login(loginId, "wrong12345");
        login(loginId, PASSWORD).andExpect(status().isOk());
        for (int i = 1; i <= 4; i++) login(loginId, "wrong12345").andExpect(status().isUnauthorized());

        login(loginId, PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("없는 아이디도 똑같이 5회째 잠김 (잠금으로 아이디 존재를 알 수 없음)")
    void unknownIdLocksTheSameWay() throws Exception {
        String unknown = newLoginId();
        for (int i = 1; i <= 4; i++) login(unknown, "wrong12345").andExpect(status().isUnauthorized());
        login(unknown, "wrong12345").andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("대문자로 입력해도 같은 아이디로 취급 (잠금 우회 불가, 로그인 가능)")
    void loginIdIsCaseInsensitive() throws Exception {
        String loginId = newLoginId();
        signupOk(loginId);

        login(loginId.toUpperCase(), PASSWORD).andExpect(status().isOk());
        for (int i = 1; i <= 5; i++) login(i % 2 == 0 ? loginId : loginId.toUpperCase(), "wrong12345");
        login(loginId, PASSWORD).andExpect(status().isTooManyRequests());
    }

    ResultActions login(String loginId, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json("loginId", loginId, "password", password)));
    }
}
