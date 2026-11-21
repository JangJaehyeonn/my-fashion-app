package com.fashionapp.domain.consent;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Consent history (FR-04). The users.age_confirmed_at / users.vton_consent_at columns stay the source of truth for
 * existing checks (AgeGateInterceptor, VtonService); this service only keeps the history next to them.
 */
@Service
@RequiredArgsConstructor
public class ConsentService {

    private final ConsentRecordRepository consentRecordRepository;
    private final Clock clock;

    /**
     * Records an agreement unless one is already active (idempotent: repeated grants keep a single active row).
     *
     * @param agreedAt when the user agreed; null means now
     */
    @Transactional
    public void ensureAgreed(UUID userId, ConsentType type, LocalDateTime agreedAt) {
        if (!consentRecordRepository.findByUserIdAndConsentTypeAndRevokedAtIsNull(userId, type).isEmpty()) {
            return;
        }
        LocalDateTime at = agreedAt != null ? agreedAt : LocalDateTime.now(clock);
        consentRecordRepository.save(ConsentRecord.agreed(userId, type, type.currentVersion(), at));
    }

    /** Sets the revoke time on every active row of this type. Rows are never deleted. */
    @Transactional
    public void revoke(UUID userId, ConsentType type) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<ConsentRecord> active = consentRecordRepository.findByUserIdAndConsentTypeAndRevokedAtIsNull(userId, type);
        active.forEach(record -> record.revoke(now));
    }

    /** Account deletion only. */
    @Transactional
    public void deleteAll(UUID userId) {
        consentRecordRepository.deleteAllByUserId(userId);
    }
}
