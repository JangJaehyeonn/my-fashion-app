package com.fashionapp.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserPrincipalTest {

    private User user(String email) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .nickname("테스터")
                .provider(User.AuthProvider.kakao)
                .providerId("kakao-1")
                .build();
    }

    @Test
    @DisplayName("이메일이 있으면 username은 이메일")
    void usernameIsEmailWhenPresent() {
        UserPrincipal principal = UserPrincipal.create(user("test@example.com"));

        assertThat(principal.getUsername()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("이메일 동의가 없는 Kakao 사용자도 username이 비지 않는다 (OAuth2 principalName 필수값)")
    void usernameFallsBackToIdWhenEmailMissing() {
        User noEmail = user(null);
        UserPrincipal principal = UserPrincipal.create(noEmail);

        assertThat(principal.getUsername()).isEqualTo(String.valueOf(noEmail.getId()));
    }

    @Test
    @DisplayName("이메일이 공백이어도 id로 대체")
    void usernameFallsBackToIdWhenEmailBlank() {
        User blank = user("  ");
        UserPrincipal principal = UserPrincipal.create(blank);

        assertThat(principal.getUsername()).isEqualTo(String.valueOf(blank.getId()));
    }
}
