package com.fashionapp.domain.weather;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionapp.infra.AiServerClient;
import com.fashionapp.infra.AiWeatherResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    // 날씨는 위치 구분 없이 앱 전체가 공유하는 값 하나뿐이라 캐시 키도 고정값 하나만 사용
    private static final String WEATHER_KEY = "weather:current";

    private final AiServerClient aiServerClient;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // 1단계 캐시 (Caffeine, 서버 프로세스 로컬 메모리).
    // Redis 왕복조차 없이 즉시 응답하기 위한 용도라 TTL을 짧게(10분) 잡음 —
    // 인스턴스 재시작/재배포 시 사라지는 건 감수하고, 그 경우 2단계(Redis)가 받쳐줌.
    private final Cache<String, AiWeatherResponse> localCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(1)
            .build();

    public AiWeatherResponse getWeather() {
        // 1단계: 로컬 캐시 히트 — 네트워크 왕복 없이 즉시 반환
        AiWeatherResponse fromLocal = localCache.getIfPresent(WEATHER_KEY);
        if (fromLocal != null) {
            return fromLocal;
        }

        // 2단계: Redis 캐시 — 로컬 캐시가 비어 있을 때(콜드 스타트, 재시작 직후 등)
        // 외부 API를 다시 부르지 않고 다른 인스턴스가 채워둔 값을 재사용
        AiWeatherResponse fromRedis = getFromRedis();
        if (fromRedis != null) {
            localCache.put(WEATHER_KEY, fromRedis);
            return fromRedis;
        }

        // 3단계: 둘 다 미스 — 외부 날씨 API 호출 후 로컬/Redis 캐시 모두 채움
        AiWeatherResponse fresh = aiServerClient.getWeather();
        localCache.put(WEATHER_KEY, fresh);
        putToRedis(fresh);
        return fresh;
    }

    private AiWeatherResponse getFromRedis() {
        String json = redisTemplate.opsForValue().get(WEATHER_KEY);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AiWeatherResponse.class);
        } catch (JsonProcessingException e) {
            log.warn("Redis 날씨 캐시 역직렬화 실패, 외부 API로 새로 조회: {}", e.getMessage());
            return null;
        }
    }

    private void putToRedis(AiWeatherResponse weather) {
        try {
            // Redis TTL(30분)은 로컬 캐시(10분)보다 길게 잡아, 로컬 캐시가 비어도
            // 외부 API 호출 없이 Redis에서 바로 채울 수 있는 구간을 넓게 둠
            redisTemplate.opsForValue().set(WEATHER_KEY, objectMapper.writeValueAsString(weather), Duration.ofMinutes(30));
        } catch (JsonProcessingException e) {
            log.warn("Redis 날씨 캐시 저장 실패, 이번 응답은 캐시 없이 반환: {}", e.getMessage());
        }
    }
}
