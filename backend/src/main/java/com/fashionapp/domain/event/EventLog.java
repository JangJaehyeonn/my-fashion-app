package com.fashionapp.domain.event;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * FR-14: one core user action. The entity only describes the table (ddl-auto) and is read in tests; rows are
 * inserted with native SQL by {@link EventRecorder} and removed by account deletion and the 1-year retention job.
 *
 * <p>user_id has no FK on purpose (see docs/adr/0013-event-log.md), like wear_records and purchase_checks.
 * props only holds codes and numbers (EventRecorder drops anything else), never names, photos or locations.
 */
@Entity
@Table(name = "events", indexes = {
        @Index(name = "idx_events_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_events_created", columnList = "created_at")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "type", length = 30)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "props", columnDefinition = "jsonb")
    private String props;

    @Column(name = "created_at")
    private Instant createdAt;
}
