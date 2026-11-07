package com.fashionapp.global.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

import java.util.ArrayList;
import java.util.List;

/**
 * 운영 프로필(prod)에서 운영 전용 값이 비어 있거나 로컬 값 그대로면 기동을 중단한다.
 * 조용히 localhost로 동작하다가 배포 후에야 이미지·로그인·CORS가 깨지는 일을 막기 위함.
 *
 * <p>빈이 아니라 EnvironmentPostProcessor로 두는 이유: 빈으로 만들면 값이 비어서 생기는 다른 빈의 의존성 오류
 * (예: 저장소 빈 없음)에 가려 원인이 안 보인다. 설정 파일이 다 읽힌 직후(맨 마지막 순서) 컨텍스트 생성 전에 검사한다.
 * 등록: META-INF/spring.factories
 */
public class ProdConfigValidator implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        if (!env.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        List<String> problems = problems(
                env.getProperty("storage.type", ""),
                env.getProperty("app.public-base-url", ""),
                env.getProperty("app.frontend-url", ""),
                env.getProperty("app.cors.allowed-origins", ""));
        if (!problems.isEmpty()) {
            throw new ProdConfigException(problems);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    /** 문제 목록 (비어 있으면 통과) */
    static List<String> problems(String storageType, String publicBaseUrl, String frontendUrl, String corsAllowedOrigins) {
        List<String> problems = new ArrayList<>();
        if (!List.of("local", "s3").contains(storageType)) {
            problems.add("STORAGE_TYPE: local 또는 s3 (현재: '" + storageType + "')");
        }
        requireHttps(problems, "PUBLIC_BASE_URL", publicBaseUrl);
        requireHttps(problems, "FRONTEND_URL", frontendUrl);
        if (corsAllowedOrigins.isBlank()) {
            problems.add("CORS_ALLOWED_ORIGINS: 허용할 웹 출처 (쉼표로 구분)");
        } else {
            for (String origin : corsAllowedOrigins.split(",")) {
                requireHttps(problems, "CORS_ALLOWED_ORIGINS", origin.trim());
            }
        }
        return problems;
    }

    private static void requireHttps(List<String> problems, String name, String value) {
        if (value == null || value.isBlank()) {
            problems.add(name + ": 값이 비어 있습니다 (https 주소 필요)");
        } else if (!value.startsWith("https://") || value.contains("localhost") || value.contains("127.0.0.1")) {
            problems.add(name + ": 운영에서는 localhost가 아닌 https 주소여야 합니다 (현재: '" + value + "')");
        }
    }

    public static class ProdConfigException extends RuntimeException {
        private final List<String> problems;

        ProdConfigException(List<String> problems) {
            super("운영 프로필 설정이 올바르지 않습니다: " + String.join(" / ", problems));
            this.problems = problems;
        }

        public List<String> getProblems() {
            return problems;
        }
    }
}
