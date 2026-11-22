package com.fashionapp.domain.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * FR-14: records a core user action in {@code events}. Recording never fails the caller's request:
 * <ul>
 *   <li>inside a transaction the row is written after it commits (a rolled-back action leaves no event, and a failed
 *       event write cannot abort the caller's transaction);</li>
 *   <li>the write runs in its own transaction and every exception is logged and swallowed.</li>
 * </ul>
 * props keep only numbers, booleans and upper-case codes (TOP, BUY, LOCAL...). Any other string — a name, a URL,
 * free text — is dropped here, so personal data cannot reach the table even if a caller passes it by mistake.
 */
@Slf4j
@Component
public class EventRecorder {

    static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,39}");

    private final EventLogRepository repository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate ownTransaction;
    private final Clock clock;

    public EventRecorder(EventLogRepository repository, ObjectMapper objectMapper,
                         PlatformTransactionManager transactionManager, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    public void record(UUID userId, EventType type) {
        record(userId, type, Map.of());
    }

    public void record(UUID userId, EventType type, Map<String, ?> props) {
        if (userId == null || type == null) {
            return;
        }
        Instant at = clock.instant();
        String json = toJson(sanitize(props));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    write(userId, type, json, at);
                }
            });
        } else {
            write(userId, type, json, at);
        }
    }

    /** Keeps numbers, booleans and code strings; drops nulls, other strings and nested values. Sorted for stable JSON. */
    static Map<String, Object> sanitize(Map<String, ?> props) {
        Map<String, Object> clean = new TreeMap<>();
        if (props == null) {
            return clean;
        }
        props.forEach((key, value) -> {
            if (key != null && isAllowed(value)) {
                clean.put(key, value instanceof Enum<?> e ? e.name() : value);
            }
        });
        return clean;
    }

    private static boolean isAllowed(Object value) {
        if (value instanceof Number || value instanceof Boolean) {
            return true;
        }
        if (value instanceof Enum<?> e) {
            return CODE.matcher(e.name()).matches();
        }
        return value instanceof String s && CODE.matcher(s).matches();
    }

    private String toJson(Map<String, Object> props) {
        try {
            return objectMapper.writeValueAsString(props);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private void write(UUID userId, EventType type, String json, Instant at) {
        try {
            ownTransaction.executeWithoutResult(status -> repository.insert(userId, type.name(), json, at));
        } catch (RuntimeException e) {
            // The action itself already succeeded; losing one analytics event is acceptable
            log.warn("event.record failed type={} userId={}: {}", type, userId, e.getClass().getSimpleName());
        }
    }
}
