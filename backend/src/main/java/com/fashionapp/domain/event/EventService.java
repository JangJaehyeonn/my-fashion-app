package com.fashionapp.domain.event;

import com.fashionapp.domain.wear.WearCalendar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

/** FR-14: the daily app-open event, account deletion of events and the 1-year retention. */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    static final int RETENTION_YEARS = 1;

    private final EventLogRepository repository;
    private final EventRecorder recorder;
    private final Clock clock;

    /**
     * The app sends this once a day; a repeat on the same Korean day is ignored so the revisit metric counts days.
     * Never fails: an unreadable or unwritable table only means the event is missing.
     */
    public void appOpened(UUID userId) {
        Instant dayStart = WearCalendar.today(clock).atStartOfDay(WearCalendar.ZONE).toInstant();
        try {
            if (repository.existsByUserIdAndTypeAndCreatedAtGreaterThanEqual(userId, EventType.APP_OPENED.name(), dayStart)) {
                return;
            }
        } catch (RuntimeException e) {
            log.warn("event.appOpened lookup failed userId={}: {}", userId, e.getClass().getSimpleName());
            return;
        }
        recorder.record(userId, EventType.APP_OPENED);
    }

    /** Account deletion, inside its DB transaction. */
    public void deleteAll(UUID userId) {
        repository.deleteAllByUserId(userId);
    }

    /** Events older than one year (Korean calendar) are removed once a day. */
    @Scheduled(cron = "${wearon.events.retention-cron:0 30 4 * * *}", zone = "Asia/Seoul")
    public void deleteExpired() {
        int deleted = repository.deleteCreatedBefore(retentionCutoff());
        if (deleted > 0) {
            log.info("event.retention deleted={}", deleted);
        }
    }

    Instant retentionCutoff() {
        return ZonedDateTime.ofInstant(clock.instant(), WearCalendar.ZONE).minusYears(RETENTION_YEARS).toInstant();
    }
}
