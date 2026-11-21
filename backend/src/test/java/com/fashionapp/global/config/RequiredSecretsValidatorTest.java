package com.fashionapp.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequiredSecretsValidatorTest {

    private final RequiredSecretsValidator validator = new RequiredSecretsValidator();
    private final SpringApplication app = new SpringApplication();

    /** Mirrors application.yml: secrets are placeholders without defaults. */
    private MockEnvironment placeholdersOnly() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("jwt.secret", "${JWT_SECRET}");
        env.setProperty("spring.datasource.password", "${DB_PASSWORD}");
        env.setProperty("spring.security.oauth2.client.registration.google.client-secret", "${GOOGLE_CLIENT_SECRET}");
        env.setProperty("spring.security.oauth2.client.registration.kakao.client-secret", "${KAKAO_CLIENT_SECRET}");
        return env;
    }

    private MockEnvironment allSecretsSet() {
        return placeholdersOnly()
                .withProperty("JWT_SECRET", "test-only-secret")
                .withProperty("DB_PASSWORD", "test-only-password")
                .withProperty("GOOGLE_CLIENT_SECRET", "test-only-google")
                .withProperty("KAKAO_CLIENT_SECRET", "test-only-kakao");
    }

    @Test
    @DisplayName("비밀값이 모두 있으면 통과")
    void passesWhenAllSecretsSet() {
        assertThatCode(() -> validator.postProcessEnvironment(allSecretsSet(), app)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("JWT_SECRET이 없으면 변수 이름을 담아 기동 실패")
    void missingJwtSecretIsNamed() {
        MockEnvironment withoutJwt = placeholdersOnly()
                .withProperty("DB_PASSWORD", "test-only-password")
                .withProperty("GOOGLE_CLIENT_SECRET", "test-only-google")
                .withProperty("KAKAO_CLIENT_SECRET", "test-only-kakao");

        assertThatThrownBy(() -> validator.postProcessEnvironment(withoutJwt, app))
                .isInstanceOf(RequiredSecretsValidator.MissingSecretsException.class)
                .hasMessageContaining("JWT_SECRET")
                .hasMessageNotContaining("DB_PASSWORD");
    }

    @Test
    @DisplayName("운영 프로필에서도 빠진 비밀값을 모두 알려 준다")
    void allMissingSecretsAreNamedInProd() {
        MockEnvironment env = placeholdersOnly();
        env.setActiveProfiles("prod");

        assertThat(RequiredSecretsValidator.missingVariables(env))
                .containsExactly("JWT_SECRET", "DB_PASSWORD", "GOOGLE_CLIENT_SECRET", "KAKAO_CLIENT_SECRET");
    }

    @Test
    @DisplayName("빈 값도 빠진 것으로 본다")
    void blankValueCountsAsMissing() {
        MockEnvironment env = allSecretsSet().withProperty("JWT_SECRET", "  ");

        assertThat(RequiredSecretsValidator.missingVariables(env)).containsExactly("JWT_SECRET");
    }

    @Test
    @DisplayName("실패 안내에 빠진 변수 이름이 들어간다")
    void failureAnalysisNamesVariables() {
        RequiredSecretsValidator.MissingSecretsException cause =
                new RequiredSecretsValidator.MissingSecretsException(List.of("JWT_SECRET"));

        FailureAnalysis analysis = new RequiredSecretsFailureAnalyzer().analyze(cause, cause);

        assertThat(analysis.getDescription()).contains("JWT_SECRET");
    }
}
