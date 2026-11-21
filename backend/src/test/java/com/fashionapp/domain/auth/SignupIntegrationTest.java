package com.fashionapp.domain.auth;

import com.fashionapp.domain.user.User;
import com.fashionapp.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/auth/signup, GET /api/auth/check-id */
class SignupIntegrationTest extends LocalAuthTestSupport {

    @Autowired PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("가입 성공: 토큰 발급, provider=local, 비밀번호는 BCrypt 해시로만 저장, 받은 토큰으로 내 정보 조회")
    void signupSucceeds() throws Exception {
        String loginId = newLoginId();
        String[] tokens = signupOk(loginId);

        User user = userRepository.findByLoginId(loginId).orElseThrow();
        assertThat(user.getProvider()).isEqualTo(User.AuthProvider.local);
        assertThat(user.getNickname()).isEqualTo("테스터");
        assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2");
        assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + tokens[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider").value("local"))
                .andExpect(jsonPath("$.data.loginId").value(loginId))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("같은 아이디로 다시 가입하면 409")
    void duplicateLoginIdIs409() throws Exception {
        String loginId = newLoginId();
        signupOk(loginId);

        signup(loginId, "other5678", "다른사람")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorCode.DUPLICATE_LOGIN_ID.getMessage()));
    }

    @ParameterizedTest(name = "아이디 규칙 위반: \"{0}\"")
    @ValueSource(strings = {"abc", "abcdefghijklmnopqrstu", "Abcd1234", "abc_123", "가나다라", "abc 123"})
    void invalidLoginIdIs400(String loginId) throws Exception {
        signup(loginId, PASSWORD, "닉네임")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_LOGIN_ID.getMessage()));
    }

    @ParameterizedTest(name = "비밀번호 규칙 위반: \"{0}\"")
    @ValueSource(strings = {"short1", "onlyletters", "1234567890", "a1a1a1a"})
    void invalidPasswordIs400(String password) throws Exception {
        signup(newLoginId(), password, "닉네임")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_PASSWORD.getMessage()));
    }

    @Test
    @DisplayName("비밀번호 64자 초과는 400")
    void tooLongPasswordIs400() throws Exception {
        signup(newLoginId(), "a1".repeat(33), "닉네임").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("닉네임이 비었거나 20자 초과면 400")
    void invalidNicknameIs400() throws Exception {
        signup(newLoginId(), PASSWORD, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_NICKNAME.getMessage()));
        signup(newLoginId(), PASSWORD, "가".repeat(21)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("규칙 위반 가입은 사용자를 만들지 않는다")
    void invalidSignupCreatesNothing() throws Exception {
        String loginId = newLoginId();
        signup(loginId, "short", "닉네임").andExpect(status().isBadRequest());
        assertThat(userRepository.existsByLoginId(loginId)).isFalse();
    }

    @Test
    @DisplayName("check-id: 없는 아이디 available=true, 가입된 아이디 false, 형식 위반 400")
    void checkId() throws Exception {
        String loginId = newLoginId();
        mockMvc.perform(get("/api/auth/check-id").param("loginId", loginId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));

        signupOk(loginId);
        mockMvc.perform(get("/api/auth/check-id").param("loginId", loginId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));

        mockMvc.perform(get("/api/auth/check-id").param("loginId", "AB"))
                .andExpect(status().isBadRequest());
    }
}
