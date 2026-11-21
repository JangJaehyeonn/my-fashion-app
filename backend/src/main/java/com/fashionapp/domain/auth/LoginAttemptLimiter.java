package com.fashionapp.domain.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 아이디별 로그인 실패 횟수와 잠금 (Redis).
 *
 * <ul>
 *   <li>{@code login:fail:{loginId}} — 연속 실패 횟수. 첫 실패부터 잠금 시간(기본 5분) 동안 유지되고 성공하면 지운다</li>
 *   <li>{@code login:lock:{loginId}} — 연속 실패가 한도(기본 5회)에 닿으면 생성, 잠금 시간 뒤 자동 만료</li>
 * </ul>
 * 가입되지 않은 아이디도 똑같이 세고 잠그므로, 잠금 응답으로 아이디 존재 여부를 알 수 없다.
 * 남의 아이디를 일부러 잠글 수 있다는 한계가 있다(5분 뒤 풀림) — IP별 제한은 출시 후 과제.
 */
@Component
public class LoginAttemptLimiter {

    private final RedisTemplate<String, String> redisTemplate;
    private final int maxFailures;
    private final Duration lockDuration;

    public LoginAttemptLimiter(RedisTemplate<String, String> redisTemplate,
                               @Value("${app.auth.login-max-failures:5}") int maxFailures,
                               @Value("${app.auth.login-lock-duration:5m}") Duration lockDuration) {
        this.redisTemplate = redisTemplate;
        this.maxFailures = maxFailures;
        this.lockDuration = lockDuration;
    }

    public boolean isLocked(String loginId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey(loginId)));
    }

    /** 실패를 기록하고, 이번 실패로 잠겼으면 true */
    public boolean recordFailure(String loginId) {
        Long count = redisTemplate.opsForValue().increment(failKey(loginId));
        if (count != null && count == 1) {
            redisTemplate.expire(failKey(loginId), lockDuration);
        }
        if (count != null && count >= maxFailures) {
            redisTemplate.opsForValue().set(lockKey(loginId), "1", lockDuration);
            redisTemplate.delete(failKey(loginId));
            return true;
        }
        return false;
    }

    public void reset(String loginId) {
        redisTemplate.delete(failKey(loginId));
    }

    /** 탈퇴 시 정리 — 같은 아이디로 재가입했을 때 이전 실패 기록이 남지 않게 */
    public void clear(String loginId) {
        redisTemplate.delete(List.of(failKey(loginId), lockKey(loginId)));
    }

    static String failKey(String loginId) {
        return "login:fail:" + loginId;
    }

    static String lockKey(String loginId) {
        return "login:lock:" + loginId;
    }
}
