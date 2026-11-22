package com.fashionapp.domain.purchase;

import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseDailyLimiterTest {

    // 2026-11-22 15:30 UTC = 2026-11-23 00:30 KST
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-22T15:30:00Z"), ZoneOffset.UTC);

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> ops;

    private final Map<String, Long> counters = new HashMap<>();
    private final UUID userId = UUID.randomUUID();
    private PurchaseDailyLimiter limiter;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(ops);
        given(ops.increment(anyString())).willAnswer(inv -> counters.merge(inv.getArgument(0), 1L, Long::sum));
        limiter = new PurchaseDailyLimiter(redisTemplate, CLOCK, 30);
    }

    @Test
    @DisplayName("하루 30번까지는 통과하고 31번째는 429 PURCHASE_CHECK_LIMIT")
    void thirtyFirstIsRejected() {
        for (int i = 0; i < 30; i++) {
            limiter.acquire(userId);
        }
        assertThatThrownBy(() -> limiter.acquire(userId)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PURCHASE_CHECK_LIMIT));
        assertThat(ErrorCode.PURCHASE_CHECK_LIMIT.getStatus().value()).isEqualTo(429);
    }

    @Test
    @DisplayName("키는 사용자와 한국 날짜로 나뉘고, 첫 요청 때만 만료(2일)를 건다")
    void keyIsPerUserAndKoreanDay() {
        limiter.acquire(userId);
        limiter.acquire(userId);

        String key = "purchase:daily:" + userId + ":2026-11-23";
        assertThat(counters).containsOnlyKeys(key);
        verify(redisTemplate, times(1)).expire(key, PurchaseDailyLimiter.KEY_TTL);
    }

    @Test
    @DisplayName("Redis가 죽어 있으면 판정을 막지 않는다")
    void redisDownAllows() {
        given(ops.increment(anyString())).willThrow(new RedisConnectionFailureException("down"));
        assertThatCode(() -> limiter.acquire(userId)).doesNotThrowAnyException();
    }
}
