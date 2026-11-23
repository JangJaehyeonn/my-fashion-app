package com.fashionapp.domain.weather;

import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.infra.AiWeatherResponse;
import com.fashionapp.infra.OpenWeatherClient;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** WeatherService: 좌표별 로컬(Caffeine) → Redis → OpenWeatherMap 3단계 조회 */
@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {

    private static final String SEOUL_KEY = "weather:37.57:126.98";

    @Mock OpenWeatherClient openWeatherClient;
    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;

    private WeatherService weatherService;

    @BeforeEach
    void setUp() {
        weatherService = new WeatherService(openWeatherClient, redisTemplate, new ObjectMapper());
    }

    private static AiWeatherResponse weather(double temp, String condition, int humidity, double wind) {
        return new AiWeatherResponse(temp, condition, humidity, wind);
    }

    @Test
    @DisplayName("좌표가 없으면 서울시청 좌표로 조회한다")
    void defaultsToSeoul() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(SEOUL_KEY)).willReturn(null);
        given(openWeatherClient.fetch(37.57, 126.98)).willReturn(weather(20, "맑음", 50, 1));

        assertThat(weatherService.getWeather(null, null).getCondition()).isEqualTo("맑음");
        verify(openWeatherClient).fetch(37.57, 126.98);
    }

    @Test
    @DisplayName("정밀 좌표를 보내도 OpenWeatherMap에는 소수 둘째 자리로 반올림한 좌표만 전달한다")
    void sendsOnlyRoundedCoordinatesUpstream() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);
        given(openWeatherClient.fetch(anyDouble(), anyDouble())).willReturn(weather(10, "비", 80, 3));

        weatherService.getWeather(35.179554321, 129.075641234);

        verify(openWeatherClient).fetch(35.18, 129.08);
        verify(valueOperations).get("weather:35.18:129.08");
    }

    @Test
    @DisplayName("로컬 캐시에 값이 있으면 Redis와 외부 API를 호출하지 않는다")
    void hitsLocalCache() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(SEOUL_KEY)).willReturn(null);
        AiWeatherResponse fresh = weather(20.5, "맑음", 55, 2.1);
        given(openWeatherClient.fetch(anyDouble(), anyDouble())).willReturn(fresh);
        weatherService.getWeather(null, null);
        Mockito.clearInvocations(openWeatherClient, redisTemplate, valueOperations);

        assertThat(weatherService.getWeather(null, null)).isSameAs(fresh);
        verify(openWeatherClient, never()).fetch(anyDouble(), anyDouble());
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("캐시 키는 소수 둘째 자리로 반올림한 좌표 — 칸이 다르면 따로 조회한다")
    void cacheIsPerRoundedCoordinates() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);
        given(openWeatherClient.fetch(anyDouble(), anyDouble())).willReturn(weather(10, "비", 80, 3));

        weatherService.getWeather(35.1796, 129.0756);   // 부산
        weatherService.getWeather(35.1801, 129.0749);   // 키 35.18/129.07 — 위 요청(129.08)과 다른 칸
        weatherService.getWeather(37.5665, 126.9780);   // 서울

        assertThat(WeatherService.cacheKey(35.1796, 129.0756)).isEqualTo("weather:35.18:129.08");
        assertThat(WeatherService.cacheKey(37.5665, 126.9780)).isEqualTo(SEOUL_KEY);
        verify(openWeatherClient, times(3)).fetch(anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("로컬 캐시가 비어 있고 Redis에 값이 있으면 외부 API 호출 없이 Redis 값을 반환한다")
    void fallsBackToRedis() throws Exception {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(SEOUL_KEY)).willReturn(new ObjectMapper().writeValueAsString(weather(18, "흐림", 70, 1.5)));

        AiWeatherResponse result = weatherService.getWeather(null, null);

        assertThat(result.getTemperature()).isEqualTo(18.0);
        assertThat(result.getHumidity()).isEqualTo(70);
        verify(openWeatherClient, never()).fetch(anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("둘 다 미스면 외부 API를 호출하고 Redis에 30분 TTL로 저장한다")
    void callsExternalApiAndFillsRedis() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(SEOUL_KEY)).willReturn(null);
        given(openWeatherClient.fetch(anyDouble(), anyDouble())).willReturn(weather(25.3, "비", 80, 3.4));

        assertThat(weatherService.getWeather(null, null).getCondition()).isEqualTo("비");
        verify(valueOperations).set(eq(SEOUL_KEY), anyString(), eq(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("Redis 값이 손상됐거나 Redis가 죽어 있어도 외부 API로 조회한다")
    void survivesRedisProblems() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(SEOUL_KEY)).willReturn("{깨진-json");
        given(openWeatherClient.fetch(anyDouble(), anyDouble())).willReturn(weather(15, "눈", 90, 4));
        assertThat(weatherService.getWeather(null, null).getCondition()).isEqualTo("눈");

        WeatherService second = new WeatherService(openWeatherClient, redisTemplate, new ObjectMapper());
        given(valueOperations.get(SEOUL_KEY)).willThrow(new IllegalStateException("redis down"));
        assertThat(second.getWeather(null, null).getCondition()).isEqualTo("눈");
    }

    @Test
    @DisplayName("범위를 벗어난 좌표는 INVALID_COORDINATES")
    void rejectsInvalidCoordinates() {
        assertThatThrownBy(() -> weatherService.getWeather(91.0, 0.0))
                .extracting(e -> ((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_COORDINATES);
        assertThatThrownBy(() -> weatherService.getWeather(0.0, -181.0))
                .extracting(e -> ((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_COORDINATES);
        verify(openWeatherClient, never()).fetch(anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("FR-28: 예보도 반올림 좌표별 로컬 → Redis → OpenWeatherMap 순서이고, 현재 날씨와 키가 다르다")
    void forecastUsesSameCacheStructure() throws Exception {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("weather:forecast:35.18:129.08")).willReturn(null);
        com.fashionapp.infra.Forecast forecast = new com.fashionapp.infra.Forecast(
                java.util.List.of(new com.fashionapp.infra.ForecastSlot(1700000000, 3.0, 0.7, 2.0)));
        given(openWeatherClient.fetchForecast(35.18, 129.08)).willReturn(forecast);

        assertThat(weatherService.getForecast(35.179554, 129.075641)).isEqualTo(forecast);
        assertThat(weatherService.getForecast(35.18, 129.08)).isSameAs(forecast);

        verify(openWeatherClient, times(1)).fetchForecast(35.18, 129.08);
        verify(valueOperations).set(eq("weather:forecast:35.18:129.08"), anyString(), eq(Duration.ofMinutes(30)));
        verify(openWeatherClient, never()).fetch(anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("FR-28: Redis에 저장된 예보를 그대로 되살린다")
    void forecastFromRedis() throws Exception {
        com.fashionapp.infra.Forecast forecast = new com.fashionapp.infra.Forecast(
                java.util.List.of(new com.fashionapp.infra.ForecastSlot(1700000000, 3.0, 0.7, 2.0)));
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("weather:forecast:37.57:126.98")).willReturn(new ObjectMapper().writeValueAsString(forecast));

        assertThat(weatherService.getForecast(null, null)).isEqualTo(forecast);
        verify(openWeatherClient, never()).fetchForecast(anyDouble(), anyDouble());
    }
}
