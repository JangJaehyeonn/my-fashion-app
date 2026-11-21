package com.fashionapp.domain.auth;

import com.fashionapp.domain.user.User;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.global.jwt.JwtTokenProvider;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PATCH /api/users/me/password */
class ChangePasswordIntegrationTest extends LocalAuthTestSupport {

    static final String NEW_PASSWORD = "newpass5678";

    @Autowired JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("변경 성공: 새 비밀번호로 로그인되고 옛 비밀번호는 401, 기존 리프레시 토큰은 401, 응답의 새 리프레시는 유효")
    void changeSucceedsAndRevokesRefreshTokens() throws Exception {
        String loginId = newLoginId();
        String[] tokens = signupOk(loginId);

        String body = change(tokens[0], PASSWORD, NEW_PASSWORD).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newRefresh = JsonPath.read(body, "$.data.refreshToken");

        refresh(tokens[1]).andExpect(status().isUnauthorized());
        refresh(newRefresh).andExpect(status().isOk());

        login(loginId, PASSWORD).andExpect(status().isUnauthorized());
        login(loginId, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 400 (401이 아님 — 앱 토큰 갱신과 구분), 비밀번호는 그대로")
    void wrongCurrentPassword() throws Exception {
        String loginId = newLoginId();
        String[] tokens = signupOk(loginId);

        change(tokens[0], "wrong12345", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_CURRENT_PASSWORD.getMessage()));
        login(loginId, PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("새 비밀번호가 규칙 위반이거나 지금과 같으면 400")
    void invalidNewPassword() throws Exception {
        String[] tokens = signupOk(newLoginId());

        change(tokens[0], PASSWORD, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_PASSWORD.getMessage()));
        change(tokens[0], PASSWORD, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorCode.SAME_PASSWORD.getMessage()));
    }

    @Test
    @DisplayName("OAuth 사용자는 비밀번호 변경 불가 (400)")
    void oauthUserCannotChangePassword() throws Exception {
        User oauth = userRepository.save(User.builder().ageConfirmedAt(java.time.LocalDateTime.now())
                .provider(User.AuthProvider.kakao).providerId("k-" + UUID.randomUUID()).nickname("카카오").build());
        try {
            change(jwtTokenProvider.createAccessToken(oauth.getId()), "anything1", NEW_PASSWORD)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(ErrorCode.PASSWORD_NOT_SUPPORTED.getMessage()));
        } finally {
            userRepository.delete(oauth);
        }
    }

    @Test
    @DisplayName("인증 없이 호출하면 401")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(patch("/api/users/me/password").contentType(MediaType.APPLICATION_JSON)
                        .content(json("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions change(String accessToken, String current, String next) throws Exception {
        return mockMvc.perform(patch("/api/users/me/password")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("currentPassword", current, "newPassword", next)));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json("refreshToken", refreshToken)));
    }

    private ResultActions login(String loginId, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json("loginId", loginId, "password", password)));
    }
}
