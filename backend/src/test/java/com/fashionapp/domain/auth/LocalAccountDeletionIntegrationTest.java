package com.fashionapp.domain.auth;

import com.fashionapp.infra.ImageStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 아이디·비밀번호(local) 사용자의 회원 탈퇴 (S3 삭제는 목) */
class LocalAccountDeletionIntegrationTest extends LocalAuthTestSupport {

    @MockBean ImageStorage imageStorage;

    @Test
    @DisplayName("local 사용자 탈퇴: 사용자·리프레시 토큰 삭제, 기존 토큰 401, 같은 아이디로 로그인 불가, 같은 아이디로 재가입 가능")
    void localUserCanDeleteAccountAndSignUpAgain() throws Exception {
        String loginId = newLoginId();
        String[] tokens = signupOk(loginId);
        UUID userId = userRepository.findByLoginId(loginId).orElseThrow().getId();

        mockMvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + tokens[0]))
                .andExpect(status().isOk());

        assertThat(userRepository.existsById(userId)).isFalse();
        assertThat(userRepository.existsByLoginId(loginId)).isFalse();
        assertThat(redisTemplate.hasKey("refresh:" + userId)).isFalse();

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + tokens[0]))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json("refreshToken", tokens[1])))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("loginId", loginId, "password", PASSWORD)))
                .andExpect(status().isUnauthorized());

        String[] again = signupOk(loginId);
        assertThat(userRepository.findByLoginId(loginId).orElseThrow().getId()).isNotEqualTo(userId);
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + again[0])).andExpect(status().isOk());
    }

    @Test
    @DisplayName("탈퇴하면 로그인 실패 기록도 지워져, 재가입한 같은 아이디가 이전 실패 때문에 잠기지 않는다")
    void deletionClearsLoginFailures() throws Exception {
        String loginId = newLoginId();
        String[] tokens = signupOk(loginId);
        for (int i = 1; i <= 4; i++) {
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(json("loginId", loginId, "password", "wrong12345")));
        }
        assertThat(redisTemplate.hasKey(LoginAttemptLimiter.failKey(loginId))).isTrue();

        mockMvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + tokens[0])).andExpect(status().isOk());

        assertThat(redisTemplate.hasKey(LoginAttemptLimiter.failKey(loginId))).isFalse();
    }
}
