package com.fashionapp.infra;

import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** OpenWeatherClient: 실제 OpenWeatherMap 대신 MockWebServer로 응답 매핑·오류 처리를 검증 */
class OpenWeatherClientTest {

    private static final String KEY = "SECRET-TEST-KEY";

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private OpenWeatherClient client(String key) {
        return new OpenWeatherClient(server.url("/data/2.5/weather").toString(), key,
                Duration.ofSeconds(1), Duration.ofMillis(500));
    }

    private static MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    @Test
    @DisplayName("응답을 온도·날씨(한글)·습도·풍속으로 매핑하고, 좌표·단위·키를 쿼리로 보낸다")
    void mapsResponse() throws Exception {
        server.enqueue(json("{\"weather\":[{\"main\":\"Rain\"}],\"main\":{\"temp\":12.4,\"humidity\":81},\"wind\":{\"speed\":3.2}}"));

        AiWeatherResponse w = client(KEY).fetch(37.5665, 126.978);

        assertThat(w.getTemperature()).isEqualTo(12.4);
        assertThat(w.getCondition()).isEqualTo("비");
        assertThat(w.getHumidity()).isEqualTo(81);
        assertThat(w.getWindSpeed()).isEqualTo(3.2);
        RecordedRequest req = server.takeRequest();
        assertThat(req.getRequestUrl().queryParameter("lat")).isEqualTo("37.5665");
        assertThat(req.getRequestUrl().queryParameter("lon")).isEqualTo("126.978");
        assertThat(req.getRequestUrl().queryParameter("units")).isEqualTo("metric");
        assertThat(req.getRequestUrl().queryParameter("appid")).isEqualTo(KEY);
    }

    @Test
    @DisplayName("모르는 날씨 코드는 원문 그대로 반환")
    void unknownConditionPassesThrough() {
        server.enqueue(json("{\"weather\":[{\"main\":\"Tornado\"}],\"main\":{\"temp\":1,\"humidity\":1},\"wind\":{\"speed\":1}}"));
        assertThat(client(KEY).fetch(0, 0).getCondition()).isEqualTo("Tornado");
    }

    @Test
    @DisplayName("키가 비어 있으면 호출하지 않고 WEATHER_UNAVAILABLE")
    void missingKey() {
        assertThatThrownBy(() -> client("").fetch(0, 0))
                .extracting(e -> ((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.WEATHER_UNAVAILABLE);
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("401·500·타임아웃·연결 거부는 모두 WEATHER_UNAVAILABLE이고, 예외 메시지에 키가 들어가지 않는다")
    void failuresMapToUnavailableWithoutLeakingKey() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(401));
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(json("{}").setHeadersDelay(2, java.util.concurrent.TimeUnit.SECONDS));
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> client(KEY).fetch(0, 0))
                    .isInstanceOf(CustomException.class)
                    .satisfies(e -> {
                        assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.WEATHER_UNAVAILABLE);
                        assertThat(String.valueOf(e.getMessage())).doesNotContain(KEY);
                    });
        }
        server.shutdown();
        assertThatThrownBy(() -> client(KEY).fetch(0, 0))
                .extracting(e -> ((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.WEATHER_UNAVAILABLE);
    }
}
