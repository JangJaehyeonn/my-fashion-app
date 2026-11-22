package com.fashionapp.domain.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class EventRecorderTest {

    private static final Instant NOW = Instant.parse("2026-11-20T03:00:00Z");

    private final EventLogRepository repository = mock(EventLogRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final UUID userId = UUID.randomUUID();
    private EventRecorder recorder;

    @BeforeEach
    void setUp() {
        given(transactionManager.getTransaction(any())).willReturn(new SimpleTransactionStatus());
        recorder = new EventRecorder(repository, new ObjectMapper(), transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("트랜잭션 밖이면 바로 type·props(JSON)·주입 시계 시각으로 쓴다")
    void writesImmediatelyOutsideTransaction() {
        recorder.record(userId, EventType.PURCHASE_CHECKED, Map.of("verdict", "BUY", "plus", 7));

        verify(repository).insert(userId, "PURCHASE_CHECKED", "{\"plus\":7,\"verdict\":\"BUY\"}", NOW);
    }

    @Test
    @DisplayName("트랜잭션 안이면 커밋 뒤에 쓰고, 롤백되면 쓰지 않는다")
    void defersToAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        recorder.record(userId, EventType.WEAR_LOGGED, Map.of("pieces", 3));
        verify(repository, never()).insert(any(), anyString(), anyString(), any());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        verify(repository).insert(eq(userId), eq("WEAR_LOGGED"), eq("{\"pieces\":3}"), eq(NOW));
    }

    @Test
    @DisplayName("이벤트 쓰기가 실패해도 예외를 던지지 않는다")
    void swallowsWriteFailure() {
        given(repository.insert(any(), anyString(), anyString(), any())).willThrow(new RuntimeException("events blocked"));

        assertThatCode(() -> recorder.record(userId, EventType.CLOTHES_ADDED, Map.of("category", "TOP")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("props에는 숫자·참거짓·대문자 코드만 남고 이름·URL·자유 문장·중첩 값은 빠진다")
    void sanitizeKeepsOnlyCodesAndNumbers() {
        Map<String, Object> props = new HashMap<>();
        props.put("category", "TOP");
        props.put("verdict", EventType.SIGNUP);
        props.put("plus", 3);
        props.put("priced", true);
        props.put("name", "네이비 니트");
        props.put("imageUrl", "http://img/clothes/a.jpg");
        props.put("situation", "출근 미팅");
        props.put("nested", Map.of("a", 1));
        props.put("empty", null);

        Map<String, Object> clean = EventRecorder.sanitize(props);

        assertThat(clean).containsOnlyKeys("category", "verdict", "plus", "priced");
        assertThat(clean.get("verdict")).isEqualTo("SIGNUP");
    }
}
