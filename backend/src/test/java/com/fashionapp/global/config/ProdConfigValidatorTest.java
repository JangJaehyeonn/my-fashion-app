package com.fashionapp.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProdConfigValidatorTest {

    private final ProdConfigValidator validator = new ProdConfigValidator();
    private final SpringApplication app = new SpringApplication();

    private MockEnvironment env(String... props) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        for (int i = 0; i < props.length; i += 2) {
            env.setProperty(props[i], props[i + 1]);
        }
        return env;
    }

    @Test
    @DisplayName("운영 값이 모두 올바르면 통과")
    void valid() {
        assertThatCode(() -> validator.postProcessEnvironment(env(
                "storage.type", "s3", "app.public-base-url", "https://api.example.com",
                "app.frontend-url", "https://example.com",
                "app.cors.allowed-origins", "https://example.com, https://www.example.com"), app))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("값이 비어 있으면 어떤 변수가 빠졌는지 알려 주며 실패")
    void missingValuesAreNamed() {
        assertThatThrownBy(() -> validator.postProcessEnvironment(env(), app))
                .isInstanceOf(ProdConfigValidator.ProdConfigException.class)
                .hasMessageContaining("STORAGE_TYPE")
                .hasMessageContaining("PUBLIC_BASE_URL")
                .hasMessageContaining("FRONTEND_URL")
                .hasMessageContaining("CORS_ALLOWED_ORIGINS");
    }

    @Test
    @DisplayName("localhost나 http 값이 남아 있으면 실패")
    void localValuesAreRejected() {
        assertThat(ProdConfigValidator.problems("local", "http://localhost:8080", "https://example.com", "https://example.com"))
                .singleElement().asString().contains("PUBLIC_BASE_URL");
        assertThat(ProdConfigValidator.problems("local", "https://api.example.com", "https://example.com", "http://localhost:3000"))
                .singleElement().asString().contains("CORS_ALLOWED_ORIGINS");
    }

    @Test
    @DisplayName("prod 프로필이 아니면 검사하지 않는다 (로컬 기본값 허용)")
    void skippedOutsideProd() {
        assertThatCode(() -> validator.postProcessEnvironment(new MockEnvironment(), app)).doesNotThrowAnyException();
    }
}
