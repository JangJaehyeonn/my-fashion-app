package com.fashionapp.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * AI 서버 호출용 RestClient. 타임아웃이 없으면 AI 서버나 HF Space가 멈췄을 때 요청 스레드가 무한 대기하므로
 * 일반 호출과 가상 피팅(VTON)을 서로 다른 read 타임아웃으로 나눈다 (값은 application.yml app.ai-server.*).
 */
@Configuration
public class RestClientConfig {

    public static final String AI_REST_CLIENT = "aiRestClient";
    public static final String AI_VTON_REST_CLIENT = "aiVtonRestClient";

    @Bean(AI_REST_CLIENT)
    public RestClient aiRestClient(
            @Value("${app.ai-server.connect-timeout}") Duration connectTimeout,
            @Value("${app.ai-server.read-timeout}") Duration readTimeout) {
        return build(connectTimeout, readTimeout);
    }

    @Bean(AI_VTON_REST_CLIENT)
    public RestClient aiVtonRestClient(
            @Value("${app.ai-server.connect-timeout}") Duration connectTimeout,
            @Value("${app.ai-server.vton-read-timeout}") Duration readTimeout) {
        return build(connectTimeout, readTimeout);
    }

    public static RestClient build(Duration connectTimeout, Duration readTimeout) {
        // SimpleClientHttpRequestFactory: JDK HttpClient의 Expect: 100-continue로 FastAPI가 빈 멀티파트를 받던 문제 회피 (2026-06-21)
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setBufferRequestBody(true);
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
