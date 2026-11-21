package com.fashionapp.domain.consent;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One consent given by a user. Revoking never deletes the row; it only sets {@code revokedAt}.
 * Agreeing again after a revoke inserts a new row. Rows are deleted only on account deletion.
 *
 * <p>user_id has no FK on purpose (see docs/adr/0005-consent-records.md); AccountDeletionService deletes the rows.
 */
@Entity
@Table(name = "consent_records", indexes = {
        @Index(name = "idx_consent_records_user_type", columnList = "user_id, consent_type")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", length = 30)
    private ConsentType consentType;

    @Column(name = "version", length = 30)
    private String version;

    @Column(name = "agreed_at")
    private LocalDateTime agreedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    private ConsentRecord(UUID userId, ConsentType consentType, String version, LocalDateTime agreedAt) {
        this.userId = userId;
        this.consentType = consentType;
        this.version = version;
        this.agreedAt = agreedAt;
    }

    public static ConsentRecord agreed(UUID userId, ConsentType type, String version, LocalDateTime agreedAt) {
        return new ConsentRecord(userId, type, version, agreedAt);
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public void revoke(LocalDateTime at) {
        if (revokedAt == null) {
            this.revokedAt = at;
        }
    }
}
