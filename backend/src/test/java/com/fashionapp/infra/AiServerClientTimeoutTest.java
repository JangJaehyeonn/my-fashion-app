package com.fashionapp.infra;

import com.fashionapp.global.config.RestClientConfig;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AiServerClient 타임아웃·오류 응답 매핑. 실제 AI 서버 대신 MockWebServer가 지연·오류 응답을 흉내 낸다.
 * 일반 read 타임아웃 300ms, VTON read 타임아웃 1.5s로 줄여서 검증.
 */
class AiServerClientTimeoutTest {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration READ_TIMEOUT = Duration.ofMillis(300);
    private static final Duration VTON_READ_TIMEOUT = Duration.ofMillis(1500);

    private MockWebServer server;
    private ExecutorService executor;
    private AiServerClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        executor = Executors.newFixedThreadPool(2);
        client = new AiServerClient(
                RestClientConfig.build(CONNECT_TIMEOUT, READ_TIMEOUT),
                RestClientConfig.build(CONNECT_TIMEOUT, VTON_READ_TIMEOUT),
                executor,
                server.url("").toString().replaceAll("/$", ""));
    }

    @AfterEach
    void tearDown() throws IOException {
        executor.shutdownNow();
        server.shutdown();
    }

    @Test
    @DisplayName("제한 시간 안에 응답하면 정상 파싱")
    void fastResponseSucceeds() {
        server.enqueue(json("{\"temperature\":12.5,\"condition\":\"맑음\",\"humidity\":40,\"windSpeed\":2.1}"));

        AiWeatherResponse weather = client.getWeather();

        assertThat(weather.getTemperature()).isEqualTo(12.5);
    }

    @Test
    @DisplayName("응답이 read 타임아웃보다 늦으면 AI_SERVER_TIMEOUT(504)")
    void slowResponseTimesOut() {
        server.enqueue(json("{\"category\":\"TOP\",\"color\":\"블랙\",\"name\":\"티셔츠\"}")
                .setHeadersDelay(1, TimeUnit.SECONDS));

        assertThatThrownBy(() -> client.classifyClothes(image()))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_SERVER_TIMEOUT);
    }

    @Test
    @DisplayName("비동기 호출의 타임아웃도 CompletionException 안에 AI_SERVER_TIMEOUT으로 전달")
    void asyncSlowResponseTimesOut() {
        server.enqueue(json("{\"outfits\":[]}").setHeadersDelay(1, TimeUnit.SECONDS));

        AiClosetRecommendRequest request = new AiClosetRecommendRequest(
                new AiSituationRecommendRequest.WeatherInfo(12.5, "맑음"), "DAILY", null, java.util.List.of());

        assertThatThrownBy(() -> client.recommendOutfitsByCloset(request).join())
                .isInstanceOf(CompletionException.class)
                .cause()
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_SERVER_TIMEOUT);
    }

    @Test
    @DisplayName("VTON은 긴 타임아웃 — 일반 타임아웃(300ms)보다 늦어도 VTON 한도 안이면 성공")
    void vtonUsesLongerTimeout() {
        server.enqueue(json("{\"resultImageBase64\":\"aGVsbG8=\"}").setHeadersDelay(700, TimeUnit.MILLISECONDS));

        AiVtonResponse result = client.virtualTryOn(image(), image(), "셔츠").join();

        assertThat(result.getResultImageBase64()).isEqualTo("aGVsbG8=");
    }

    @Test
    @DisplayName("VTON도 자기 한도를 넘기면 AI_SERVER_TIMEOUT")
    void vtonTimesOutAfterItsLimit() {
        server.enqueue(json("{\"resultImageBase64\":\"aGVsbG8=\"}").setHeadersDelay(3, TimeUnit.SECONDS));

        assertThatThrownBy(() -> client.virtualTryOn(image(), image(), "셔츠").join())
                .cause()
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_SERVER_TIMEOUT);
    }

    @Test
    @DisplayName("AI 서버가 오류 응답(500)이면 AI_SERVER_ERROR(503)")
    void serverErrorMapsToAiServerError() {
        server.enqueue(new MockResponse().setResponseCode(500));

        assertThatThrownBy(() -> client.getWeather())
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_SERVER_ERROR);
    }

    @Test
    @DisplayName("AI 서버가 꺼져 있으면(연결 거부) AI_SERVER_ERROR(503)")
    void connectionRefusedMapsToAiServerError() throws IOException {
        server.shutdown();

        assertThatThrownBy(() -> client.getWeather())
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_SERVER_ERROR);
    }

    private static MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    private static MockMultipartFile image() {
        return new MockMultipartFile("image", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }
}
