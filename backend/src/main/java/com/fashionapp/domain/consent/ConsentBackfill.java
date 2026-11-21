package com.fashionapp.domain.consent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Copies existing users.age_confirmed_at / users.vton_consent_at into consent_records once (idempotent, on startup).
 * Same statements as db/phase9_consent_records.sql, so a missed manual run does not lose history.
 * A user who already has an active row of that type is skipped; the users columns are not changed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsentBackfill implements ApplicationRunner {

    static final String BACKFILL_SQL = """
            INSERT INTO consent_records (id, user_id, consent_type, version, agreed_at, revoked_at)
            SELECT gen_random_uuid(), u.id, ?, ?, u.%1$s, NULL
            FROM users u
            WHERE u.%1$s IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM consent_records r
                              WHERE r.user_id = u.id AND r.consent_type = ? AND r.revoked_at IS NULL)
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        int age = backfill("age_confirmed_at", ConsentType.AGE_OVER_14);
        int vton = backfill("vton_consent_at", ConsentType.VTON);
        if (age + vton > 0) {
            log.info("consent backfill: age={} vton={}", age, vton);
        }
    }

    private int backfill(String column, ConsentType type) {
        return jdbcTemplate.update(BACKFILL_SQL.formatted(column), type.name(), ConsentType.LEGACY_VERSION, type.name());
    }
}
