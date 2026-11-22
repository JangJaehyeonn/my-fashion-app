package com.fashionapp.domain.purchase;

import com.fashionapp.domain.wear.WearCalendar;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

/**
 * FR-12: at most {@code limit} purchase checks per user per Korean calendar day (Redis counter
 * {@code purchase:daily:{userId}:{yyyy-MM-dd}}, kept two days). The day comes from the injected clock.
 *
 * <p>If Redis is down the check is allowed (logged): the limit protects AI cost, it is not a security boundary.
 */
@Slf4j
@Component
public class PurchaseDailyLimiter {

    static final Duration KEY_TTL = Duration.ofDays(2);

    private final RedisTemplate<String, String> redisTemplate;
    private final Clock clock;
    private final int limit;

    public PurchaseDailyLimiter(RedisTemplate<String, String> redisTemplate, Clock clock,
                                @Value("${wearon.purchase-check.daily-limit:30}") int limit) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
        this.limit = limit;
    }

    /** Counts this request; throws 429 PURCHASE_CHECK_LIMIT when it is over the daily limit. */
    public void acquire(UUID userId) {
        String key = key(userId, WearCalendar.today(clock));
        Long count;
        try {
            count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, KEY_TTL);
            }
        } catch (RuntimeException e) {
            log.warn("purchase daily limit unavailable: {}", e.getClass().getSimpleName());
            return;
        }
        if (count != null && count > limit) {
            throw new CustomException(ErrorCode.PURCHASE_CHECK_LIMIT);
        }
    }

    static String key(UUID userId, LocalDate day) {
        return "purchase:daily:" + userId + ":" + day;
    }
}
