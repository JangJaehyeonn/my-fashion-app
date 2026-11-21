package com.fashionapp.global.config;

import com.fashionapp.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * users.provider의 CHECK 제약을 AuthProvider enum 값과 맞춘다 (멱등, 기동 시 1회).
 *
 * <p>Hibernate 6은 테이블을 처음 만들 때 @Enumerated(STRING) 컬럼에 허용값 CHECK 제약을 붙이지만, 이 프로젝트가 쓰는
 * ddl-auto: update는 나중에 enum 값이 늘어도 그 제약을 고치지 않는다. 그래서 local 추가 후 그대로 두면 아이디 가입이
 * 제약 위반(500)으로 실패한다. 수동 스크립트(db/phase8_local_auth.sql)와 같은 일을 하되, 배포 때 실행을 빠뜨려도
 * 깨지지 않도록 기동 시 자동으로 확인한다. 제약이 이미 모든 값을 허용하거나(또는 제약이 없으면) 아무것도 하지 않는다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class UserSchemaPatch implements ApplicationRunner {

    static final String CONSTRAINT = "users_provider_check";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        List<String> definitions = jdbcTemplate.queryForList(
                "select pg_get_constraintdef(oid) from pg_constraint where conrelid = 'users'::regclass and conname = ?",
                String.class, CONSTRAINT);
        if (definitions.isEmpty()) {
            return;
        }
        String definition = definitions.get(0);
        boolean upToDate = Arrays.stream(User.AuthProvider.values())
                .allMatch(p -> definition.contains("'" + p.name() + "'"));
        if (upToDate) {
            return;
        }

        String allowed = Arrays.stream(User.AuthProvider.values())
                .map(p -> "'" + p.name() + "'")
                .collect(Collectors.joining(", "));
        jdbcTemplate.execute("alter table users drop constraint " + CONSTRAINT);
        jdbcTemplate.execute("alter table users add constraint " + CONSTRAINT + " check (provider in (" + allowed + "))");
        log.info("schema patch: {} updated to allow ({})", CONSTRAINT, allowed);
    }
}
