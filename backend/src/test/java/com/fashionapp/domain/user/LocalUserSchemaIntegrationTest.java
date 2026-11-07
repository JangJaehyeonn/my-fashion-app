package com.fashionapp.domain.user;

import com.fashionapp.global.config.UserSchemaPatch;
import com.fashionapp.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** provider=local 저장, login_id unique, 기존 OAuth 행 영향 없음, 스키마 패치 멱등 (실제 Postgres) */
class LocalUserSchemaIntegrationTest extends IntegrationTest {

    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserSchemaPatch userSchemaPatch;

    private final List<UUID> created = new ArrayList<>();

    @AfterEach
    void tearDown() {
        created.forEach(id -> { if (userRepository.existsById(id)) userRepository.deleteById(id); });
    }

    @Test
    @DisplayName("provider=local 사용자를 저장할 수 있다 (CHECK 제약에 local 허용)")
    void savesLocalUser() {
        User user = save(User.createLocal(loginId(), "$2a$10$dummyhashdummyhashdummyhashdummyhashdummyhashdummyha", "로컬"));

        User found = userRepository.findByLoginId(user.getLoginId()).orElseThrow();
        assertThat(found.getProvider()).isEqualTo(User.AuthProvider.local);
        assertThat(found.getProviderId()).isEqualTo(user.getLoginId());
        assertThat(found.isLocal()).isTrue();
    }

    @Test
    @DisplayName("login_id는 unique — 같은 아이디로 두 번 저장하면 제약 위반")
    void loginIdIsUnique() {
        String id = loginId();
        save(User.createLocal(id, "hash1", "첫번째"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(User.createLocal(id, "hash2", "두번째")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("OAuth 사용자는 login_id·password_hash가 null인 채로 저장된다 (기존 데이터 형태 유지)")
    void oauthUserHasNoLocalCredentials() {
        User oauth = save(User.builder().ageConfirmedAt(java.time.LocalDateTime.now()).provider(User.AuthProvider.google).providerId("g-" + UUID.randomUUID()).nickname("구글").build());

        User found = userRepository.findById(oauth.getId()).orElseThrow();
        assertThat(found.getLoginId()).isNull();
        assertThat(found.getPasswordHash()).isNull();
        assertThat(found.isLocal()).isFalse();
    }

    @Test
    @DisplayName("스키마 패치는 여러 번 실행해도 결과가 같다 (제약이 이미 최신이면 그대로)")
    void schemaPatchIsIdempotent() throws Exception {
        userSchemaPatch.run(null);
        userSchemaPatch.run(null);

        String definition = jdbcTemplate.queryForObject(
                "select pg_get_constraintdef(oid) from pg_constraint where conrelid = 'users'::regclass and conname = 'users_provider_check'",
                String.class);
        assertThat(definition).contains("'google'", "'kakao'", "'local'");
    }

    private User save(User user) {
        User saved = userRepository.saveAndFlush(user);
        created.add(saved.getId());
        return saved;
    }

    private static String loginId() {
        return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
