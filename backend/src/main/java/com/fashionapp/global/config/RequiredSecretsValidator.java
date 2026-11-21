package com.fashionapp.global.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertyResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stops startup in every profile (local and prod) when a required secret is missing, naming the missing variables.
 * Secrets have no defaults in application*.yml, so an unset variable leaves an unresolved placeholder here.
 * Runs as an EnvironmentPostProcessor (registered in META-INF/spring.factories) so the cause is reported
 * before unrelated bean creation errors hide it.
 */
public class RequiredSecretsValidator implements EnvironmentPostProcessor, Ordered {

    /** property key -> environment variable name that supplies it */
    static final Map<String, String> REQUIRED_SECRETS = requiredSecrets();

    private static Map<String, String> requiredSecrets() {
        Map<String, String> secrets = new LinkedHashMap<>();
        secrets.put("jwt.secret", "JWT_SECRET");
        secrets.put("spring.datasource.password", "DB_PASSWORD");
        secrets.put("spring.security.oauth2.client.registration.google.client-secret", "GOOGLE_CLIENT_SECRET");
        secrets.put("spring.security.oauth2.client.registration.kakao.client-secret", "KAKAO_CLIENT_SECRET");
        return Collections.unmodifiableMap(secrets);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        List<String> missing = missingVariables(env);
        if (!missing.isEmpty()) {
            throw new MissingSecretsException(missing);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    /** Environment variable names whose values are missing or blank (empty list means OK). */
    static List<String> missingVariables(PropertyResolver env) {
        List<String> missing = new ArrayList<>();
        REQUIRED_SECRETS.forEach((key, variable) -> {
            if (isBlankOrUnresolved(env, key)) {
                missing.add(variable);
            }
        });
        return missing;
    }

    private static boolean isBlankOrUnresolved(PropertyResolver env, String key) {
        try {
            String value = env.getProperty(key);
            return value == null || value.isBlank();
        } catch (IllegalArgumentException unresolvedPlaceholder) {
            return true;
        }
    }

    public static class MissingSecretsException extends RuntimeException {
        private final List<String> variables;

        MissingSecretsException(List<String> variables) {
            super("필수 비밀값 환경 변수가 없습니다: " + String.join(", ", variables));
            this.variables = List.copyOf(variables);
        }

        public List<String> getVariables() {
            return variables;
        }
    }
}
