package com.fashionapp.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fashionapp.global.config.RestClientConfig;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.Map;

/**
 * OpenWeatherMap Current Weather API를 백엔드에서 직접 호출한다 (AI 서버를 거치지 않음 — AI 서버가 없어도 날씨는 동작).
 *
 * <p>API 키는 쿼리 파라미터(appid)로 전달해야 해서, 요청 URL이 들어 있는 {@link RestClientException} 메시지를
 * 로그에 그대로 남기면 키가 노출된다. 그래서 실패 시에는 상태 코드나 예외 종류만 기록한다.
 */
@Slf4j
@Component
public class OpenWeatherClient {

    private static final Map<String, String> CONDITION_MAP = Map.ofEntries(
            Map.entry("Clear", "맑음"),
            Map.entry("Clouds", "구름많음"),
            Map.entry("Rain", "비"),
            Map.entry("Drizzle", "비"),
            Map.entry("Thunderstorm", "천둥번개"),
            Map.entry("Snow", "눈"),
            Map.entry("Mist", "안개"),
            Map.entry("Fog", "안개"),
            Map.entry("Haze", "안개"),
            Map.entry("Dust", "황사"),
            Map.entry("Sand", "황사"));

    private final RestClient restClient;
    private final String baseUrl;
    private final String apiKey;

    public OpenWeatherClient(@Value("${weather.base-url}") String baseUrl,
                             @Value("${weather.api-key:}") String apiKey,
                             @Value("${weather.connect-timeout:3s}") Duration connectTimeout,
                             @Value("${weather.read-timeout:10s}") Duration readTimeout) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.restClient = RestClientConfig.build(connectTimeout, readTimeout);
    }

    public AiWeatherResponse fetch(double lat, double lon) {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("weather: WEATHER_API_KEY 가 설정되지 않았습니다 (backend/.env)");
            throw new CustomException(ErrorCode.WEATHER_UNAVAILABLE);
        }
        var uri = UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("lat", lat)
                .queryParam("lon", lon)
                .queryParam("appid", apiKey)
                .queryParam("units", "metric")
                .queryParam("lang", "kr")
                .build().toUri();
        try {
            JsonNode data = restClient.get().uri(uri).retrieve().body(JsonNode.class);
            String main = data.path("weather").path(0).path("main").asText();
            return new AiWeatherResponse(
                    data.path("main").path("temp").asDouble(),
                    CONDITION_MAP.getOrDefault(main, main),
                    data.path("main").path("humidity").asInt(),
                    data.path("wind").path("speed").asDouble());
        } catch (RestClientResponseException e) {
            // 401 = 키가 잘못됐거나 아직 활성화 전(발급 후 최대 몇 시간), 429 = 호출 한도 초과
            log.error("weather: OpenWeatherMap responded {}", e.getStatusCode().value());
            throw new CustomException(ErrorCode.WEATHER_UNAVAILABLE);
        } catch (RestClientException | NullPointerException e) {
            log.error("weather: OpenWeatherMap call failed ({})", e.getClass().getSimpleName());
            throw new CustomException(ErrorCode.WEATHER_UNAVAILABLE);
        }
    }
}
