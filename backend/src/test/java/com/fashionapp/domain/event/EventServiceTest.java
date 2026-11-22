package com.fashionapp.domain.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class EventServiceTest {

    // 2026-11-20 01:00 KST (= 11-19 16:00 UTC): the Korean day started at 11-19 15:00 UTC
    private static final Instant NOW = Instant.parse("2026-11-19T16:00:00Z");
    private static final Instant KST_DAY_START = Instant.parse("2026-11-19T15:00:00Z");

    private final EventLogRepository repository = mock(EventLogRepository.class);
    private final EventRecorder recorder = mock(EventRecorder.class);
    private final EventService service = new EventService(repository, recorder, Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("오늘(한국 날짜) 첫 앱 열기만 APP_OPENED로 남긴다")
    void firstAppOpenOfKoreanDayIsRecorded() {
        given(repository.existsByUserIdAndTypeAndCreatedAtGreaterThanEqual(userId, "APP_OPENED", KST_DAY_START))
                .willReturn(false);

        service.appOpened(userId);

        verify(recorder).record(userId, EventType.APP_OPENED);
    }

    @Test
    @DisplayName("같은 날 두 번째 앱 열기는 남기지 않는다")
    void repeatedAppOpenIsIgnored() {
        given(repository.existsByUserIdAndTypeAndCreatedAtGreaterThanEqual(userId, "APP_OPENED", KST_DAY_START))
                .willReturn(true);

        service.appOpened(userId);

        verify(recorder, never()).record(any(), any());
    }

    @Test
    @DisplayName("events를 읽을 수 없어도 앱 열기는 예외 없이 끝난다")
    void lookupFailureDoesNotFail() {
        given(repository.existsByUserIdAndTypeAndCreatedAtGreaterThanEqual(any(), anyString(), any()))
                .willThrow(new RuntimeException("db down"));

        assertThatCode(() -> service.appOpened(userId)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("보관 기간은 1년: 한국 시각 기준 1년 전보다 오래된 이벤트를 지운다")
    void retentionIsOneYear() {
        assertThat(service.retentionCutoff()).isEqualTo(Instant.parse("2025-11-19T16:00:00Z"));

        service.deleteExpired();

        verify(repository).deleteCreatedBefore(Instant.parse("2025-11-19T16:00:00Z"));
    }

    @Test
    @DisplayName("탈퇴 시 그 사용자의 이벤트를 모두 지운다")
    void deleteAllRemovesUserEvents() {
        service.deleteAll(userId);

        verify(repository).deleteAllByUserId(userId);
    }
}
