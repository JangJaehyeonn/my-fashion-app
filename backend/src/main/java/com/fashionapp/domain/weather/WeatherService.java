package com.fashionapp.domain.weather;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.infra.AiWeatherResponse;
import com.fashionapp.infra.Forecast;
import com.fashionapp.infra.OpenWeatherClient;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    // 좌표를 지정하지 않으면 서울시청 (앱이 위치 권한을 거부했거나 위치를 못 얻은 경우)
    static final double DEFAULT_LAT = 37.5665;
    static final double DEFAULT_LON = 126.9780;

    private final OpenWeatherClient openWeatherClient;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // 1단계 캐시 (Caffeine, 서버 프로세스 로컬 메모리). Redis 왕복조차 없이 즉시 응답하기 위한 용도라 TTL을 짧게(10분) 잡음 —
    // 재시작하면 사라지지만 그 경우 2단계(Redis)가 받쳐줌. 좌표별로 키가 달라 개수 상한을 둔다.
    private final Cache<String, AiWeatherResponse> localCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(500)
            .build();
    private final Cache<String, Forecast> forecastCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(500)
            .build();

    public AiWeatherResponse getWeather(Double lat, Double lon) {
        double[] at = roundedCoordinates(lat, lon);
        return cached(cacheKey(at[0], at[1]), localCache, AiWeatherResponse.class,
                () -> openWeatherClient.fetch(at[0], at[1]));
    }

    /** FR-28: 3-hour forecast of the (rounded) coordinate, through the same local → Redis → OpenWeatherMap caches. */
    public Forecast getForecast(Double lat, Double lon) {
        double[] at = roundedCoordinates(lat, lon);
        return cached(forecastCacheKey(at[0], at[1]), forecastCache, Forecast.class,
                () -> openWeatherClient.fetchForecast(at[0], at[1]));
    }

    private static double[] roundedCoordinates(Double lat, Double lon) {
        double useLat = lat == null ? DEFAULT_LAT : lat;
        double useLon = lon == null ? DEFAULT_LON : lon;
        if (useLat < -90 || useLat > 90 || useLon < -180 || useLon > 180) {
            throw new CustomException(ErrorCode.INVALID_COORDINATES);
        }
        // 소수 둘째 자리(약 1km)로 반올림한다. 앱도 반올림해 보내지만 클라이언트를 믿지 않고 서버에서 다시 반올림해서,
        // 정밀 좌표가 캐시 키·OpenWeatherMap 요청·로그 어디에도 닿지 않게 한다. 사용자 식별 정보와 묶이지 않고 근처 요청은 같은 값을 공유
        return new double[]{round2(useLat), round2(useLon)};
    }

    private <T> T cached(String key, Cache<String, T> local, Class<T> type, Supplier<T> fetch) {
        T fromLocal = local.getIfPresent(key);
        if (fromLocal != null) {
            return fromLocal;
        }

        // 2단계: Redis — 콜드 스타트·재시작 직후 외부 API를 다시 부르지 않고 이미 받아 둔 값을 재사용
        T fromRedis = getFromRedis(key, type);
        if (fromRedis != null) {
            local.put(key, fromRedis);
            return fromRedis;
        }

        // 3단계: 둘 다 미스 — OpenWeatherMap 호출 후 두 캐시를 채움
        T fresh = fetch.get();
        local.put(key, fresh);
        putToRedis(key, fresh);
        return fresh;
    }

    static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }

    static String cacheKey(double lat, double lon) {
        return String.format(Locale.ROOT, "weather:%.2f:%.2f", lat, lon);
    }

    static String forecastCacheKey(double lat, double lon) {
        return String.format(Locale.ROOT, "weather:forecast:%.2f:%.2f", lat, lon);
    }

    private <T> T getFromRedis(String key, Class<T> type) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.warn("Redis 날씨 캐시 역직렬화 실패, 새로 조회: {}", e.getMessage());
            return null;
        } catch (RuntimeException e) {
            // Redis가 죽어 있어도 날씨 조회 자체는 되게 한다
            log.warn("Redis 날씨 캐시 조회 실패, 새로 조회: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private void putToRedis(String key, Object weather) {
        try {
            // Redis TTL(30분)은 로컬 캐시(10분)보다 길게 — 로컬이 비어도 외부 API 호출 없이 채울 수 있는 구간을 넓게 둠
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(weather), Duration.ofMinutes(30));
        } catch (JsonProcessingException e) {
            log.warn("Redis 날씨 캐시 저장 실패, 이번 응답은 캐시 없이 반환: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Redis 날씨 캐시 저장 실패: {}", e.getClass().getSimpleName());
        }
    }
}
