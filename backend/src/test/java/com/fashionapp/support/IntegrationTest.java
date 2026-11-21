package com.fashionapp.support;

import com.fashionapp.infra.AiServerClient;
import com.fashionapp.infra.ImageStorage;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * 실제 SecurityFilterChain·JWT 필터·JPA·Redis까지 올리는 통합 테스트의 공통 설정.
 *
 * <p>전제: 로컬 docker compose의 postgres(5432)·redis(6379)가 떠 있어야 함
 * ({@code docker compose up -d postgres redis}). 테스트는 매번 새 사용자를 만들고 스스로 정리한다.
 *
 * <p>외부로 나가는 호출은 전부 목으로 막는다: AI 서버(유료 OpenAI·HF 호출) → {@link AiServerClient},
 * 이미지 저장·URL 변환·삭제 → {@link ImageStorage}. 실제 로컬 디스크 저장소를 검증하는 테스트는 이 클래스를 상속하지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @MockBean
    protected AiServerClient aiServerClient;

    @MockBean
    protected ImageStorage imageStorage;
}
