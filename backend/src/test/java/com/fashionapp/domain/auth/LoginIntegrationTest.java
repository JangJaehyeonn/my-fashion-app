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

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/auth/login */
class LoginIntegrationTest extends LocalAuthTestSupport {

    @Autowired JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("로그인 성공: access/refresh 발급(OAuth와 같은 형식), 리프레시는 Redis에 저장")
    void loginSucceeds() throws Exception {
        String loginId = newLoginId();
        signupOk(loginId);

        String body = login(loginId, PASSWORD).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String access = JsonPath.read(body, "$.data.accessToken");
        String refresh = JsonPath.read(body, "$.data.refreshToken");

        assertThat(jwtTokenProvider.getTokenType(access)).isEqualTo(JwtTokenProvider.ACCESS_TYPE);
        UUID userId = userRepository.findByLoginId(loginId).orElseThrow().getId();
        assertThat(redisTemplate.opsForValue().get("refresh:" + userId)).isEqualTo(refresh);
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + access)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("비밀번호가 틀리면 401 + 통일된 문구")
    void wrongPassword() throws Exception {
        String loginId = newLoginId();
        signupOk(loginId);

        login(loginId, "wrong12345")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 틀렸어요"));
    }

    @Test
    @DisplayName("없는 아이디도 비밀번호 오류와 똑같은 상태·본문 (아이디 존재 여부 노출 금지)")
    void unknownLoginIdLooksTheSame() throws Exception {
        String existing = newLoginId();
        signupOk(existing);

        String wrongPasswordBody = login(existing, "wrong12345").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String unknownIdBody = login(newLoginId(), "wrong12345").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(unknownIdBody).isEqualTo(wrongPasswordBody);
        assertThat(unknownIdBody).contains(ErrorCode.LOGIN_FAILED.getMessage());
    }

    @Test
    @DisplayName("OAuth 사용자(비밀번호 없음)는 아이디 로그인 불가 — 같은 401")
    void oauthUserCannotUsePasswordLogin() throws Exception {
        User oauth = userRepository.save(User.builder()
                .provider(User.AuthProvider.google).providerId("g-" + UUID.randomUUID()).nickname("구글").build());
        try {
            login("g-anything", PASSWORD).andExpect(status().isUnauthorized());
        } finally {
            userRepository.delete(oauth);
        }
    }

    @Test
    @DisplayName("빈 본문·빈 값도 같은 401")
    void emptyCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(ErrorCode.LOGIN_FAILED.getMessage()));
    }

    ResultActions login(String loginId, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json("loginId", loginId, "password", password)));
    }
}
