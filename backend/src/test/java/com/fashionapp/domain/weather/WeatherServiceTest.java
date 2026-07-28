package com.fashionapp.domain.weather;

import com.fashionapp.infra.AiServerClient;
import com.fashionapp.infra.AiWeatherResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * WeatherService의 로컬(Caffeine) → Redis → 외부 API 2단계 캐싱 로직 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {

    private static final String WEATHER_KEY = "weather:current";

    @Mock
    private AiServerClient aiServerClient;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private WeatherService weatherService;

    @BeforeEach
    void setUp() {
        // 매 테스트마다 새 인스턴스를 만들어 로컬 캐시를 비운 상태로 시작한다
        weatherService = new WeatherService(aiServerClient, redisTemplate, new ObjectMapper());
    }

    private AiWeatherResponse weatherResponse(double temperature, String condition, int humidity, double windSpeed) throws Exception {
        String json = String.format(
                "{\"temperature\":%s,\"condition\":\"%s\",\"humidity\":%d,\"windSpeed\":%s}",
                temperature, condition, humidity, windSpeed);
        return new ObjectMapper().readValue(json, AiWeatherResponse.class);
    }

    @Test
    @DisplayName("로컬 캐시에 값이 있으면 Redis와 외부 API를 호출하지 않고 로컬 캐시 값을 반환한다")
    void getWeather_hitsLocalCache_whenAlreadyCached() throws Exception {
        // given: 첫 호출로 로컬 캐시를 미리 채워 둔다 (이때는 로컬/Redis 모두 미스라 외부 API가 호출됨)
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(WEATHER_KEY)).willReturn(null);
        AiWeatherResponse fresh = weatherResponse(20.5, "맑음", 55, 2.1);
        given(aiServerClient.getWeather()).willReturn(fresh);
        weatherService.getWeather();
        Mockito.clearInvocations(aiServerClient, redisTemplate, valueOperations);

        // when: 로컬 캐시가 채워진 상태에서 다시 조회한다
        AiWeatherResponse result = weatherService.getWeather();

        // then: 로컬 캐시 값을 그대로 반환하고, Redis와 외부 API는 다시 호출하지 않는다
        assertThat(result).isSameAs(fresh);
        verify(aiServerClient, never()).getWeather();
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("로컬 캐시가 비어 있고 Redis에 값이 있으면 외부 API 호출 없이 Redis 값을 반환한다")
    void getWeather_fallsBackToRedis_whenLocalCacheMiss() throws Exception {
        // given
        AiWeatherResponse cachedInRedis = weatherResponse(18.0, "흐림", 70, 1.5);
        String json = new ObjectMapper().writeValueAsString(cachedInRedis);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(WEATHER_KEY)).willReturn(json);

        // when
        AiWeatherResponse result = weatherService.getWeather();

        // then
        assertThat(result.getTemperature()).isEqualTo(18.0);
        assertThat(result.getCondition()).isEqualTo("흐림");
        assertThat(result.getHumidity()).isEqualTo(70);
        verify(aiServerClient, never()).getWeather();
    }

    @Test
    @DisplayName("로컬/Redis 캐시가 모두 비어 있으면 외부 API를 호출하고 결과를 두 캐시에 모두 채운다")
    void getWeather_callsExternalApi_whenBothCachesMiss() throws Exception {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(WEATHER_KEY)).willReturn(null);
        AiWeatherResponse fresh = weatherResponse(25.3, "비", 80, 3.4);
        given(aiServerClient.getWeather()).willReturn(fresh);

        // when
        AiWeatherResponse result = weatherService.getWeather();

        // then: 외부 API 결과를 그대로 반환하고, Redis에는 10분보다 긴 TTL(30분)로 저장한다
        assertThat(result.getTemperature()).isEqualTo(25.3);
        assertThat(result.getCondition()).isEqualTo("비");
        verify(aiServerClient, times(1)).getWeather();
        verify(valueOperations).set(eq(WEATHER_KEY), anyString(), eq(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("Redis에 저장된 캐시 값이 손상되어 있으면 역직렬화 실패를 무시하고 외부 API를 호출한다")
    void getWeather_fallsBackToExternalApi_whenRedisValueIsCorrupted() throws Exception {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(WEATHER_KEY)).willReturn("{이건-유효한-json이-아님");
        AiWeatherResponse fresh = weatherResponse(15.0, "눈", 90, 4.0);
        given(aiServerClient.getWeather()).willReturn(fresh);

        // when
        AiWeatherResponse result = weatherService.getWeather();

        // then
        assertThat(result.getCondition()).isEqualTo("눈");
        verify(aiServerClient, times(1)).getWeather();
    }
}
