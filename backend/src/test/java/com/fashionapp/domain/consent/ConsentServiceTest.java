package com.fashionapp.domain.consent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** 동의 이력 규칙: 유효한 동의가 있으면 새 행을 만들지 않고, 철회는 행을 지우지 않고 시각만 남긴다 */
@ExtendWith(MockitoExtension.class)
class ConsentServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T01:00:00Z"), ZONE);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock ConsentRecordRepository repository;

    ConsentService service;
    UUID userId;

    @BeforeEach
    void setUp() {
        service = new ConsentService(repository, CLOCK);
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("유효한 동의가 없으면 현재 버전으로 새 이력을 만든다 (시각을 안 주면 주입된 시계)")
    void ensureAgreedInsertsWithCurrentVersion() {
        given(repository.findByUserIdAndConsentTypeAndRevokedAtIsNull(userId, ConsentType.VTON)).willReturn(List.of());

        service.ensureAgreed(userId, ConsentType.VTON, null);

        ArgumentCaptor<ConsentRecord> captor = ArgumentCaptor.forClass(ConsentRecord.class);
        verify(repository).save(captor.capture());
        ConsentRecord saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getConsentType()).isEqualTo(ConsentType.VTON);
        assertThat(saved.getVersion()).isEqualTo(ConsentType.VTON.currentVersion());
        assertThat(saved.getAgreedAt()).isEqualTo(NOW);
        assertThat(saved.getRevokedAt()).isNull();
    }

    @Test
    @DisplayName("동의 시각을 주면 그 시각으로 기록한다 (users 컬럼의 처음 시각과 맞춤)")
    void ensureAgreedUsesGivenTime() {
        LocalDateTime earlier = NOW.minusDays(3);
        given(repository.findByUserIdAndConsentTypeAndRevokedAtIsNull(userId, ConsentType.AGE_OVER_14)).willReturn(List.of());

        service.ensureAgreed(userId, ConsentType.AGE_OVER_14, earlier);

        ArgumentCaptor<ConsentRecord> captor = ArgumentCaptor.forClass(ConsentRecord.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getAgreedAt()).isEqualTo(earlier);
    }

    @Test
    @DisplayName("이미 유효한 동의가 있으면 새 이력을 만들지 않는다")
    void ensureAgreedIsIdempotent() {
        ConsentRecord active = ConsentRecord.agreed(userId, ConsentType.VTON, "1", NOW.minusHours(1));
        given(repository.findByUserIdAndConsentTypeAndRevokedAtIsNull(userId, ConsentType.VTON)).willReturn(List.of(active));

        service.ensureAgreed(userId, ConsentType.VTON, null);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("철회는 유효한 행에 철회 시각만 넣고 지우지 않는다")
    void revokeSetsRevokedAt() {
        ConsentRecord active = ConsentRecord.agreed(userId, ConsentType.VTON, "1", NOW.minusHours(1));
        given(repository.findByUserIdAndConsentTypeAndRevokedAtIsNull(userId, ConsentType.VTON)).willReturn(List.of(active));

        service.revoke(userId, ConsentType.VTON);

        assertThat(active.getRevokedAt()).isEqualTo(NOW);
        assertThat(active.isActive()).isFalse();
        verify(repository, never()).delete(any());
    }

    @Test
    @DisplayName("이미 철회된 행의 철회 시각은 바뀌지 않는다")
    void revokeKeepsFirstRevokeTime() {
        ConsentRecord record = ConsentRecord.agreed(userId, ConsentType.VTON, "1", NOW.minusDays(2));
        record.revoke(NOW.minusDays(1));

        record.revoke(NOW);

        assertThat(record.getRevokedAt()).isEqualTo(NOW.minusDays(1));
    }

    @Test
    @DisplayName("가입 필수 동의: 약관·방침은 지금 시각, 만 14세는 확인 시각으로 3건을 남긴다")
    void recordRequiredAgreementsSavesThreeTypes() {
        LocalDateTime ageAt = NOW.minusMinutes(1);
        given(repository.findByUserIdAndConsentTypeAndRevokedAtIsNull(any(), any())).willReturn(List.of());

        service.recordRequiredAgreements(userId, ageAt);

        ArgumentCaptor<ConsentRecord> captor = ArgumentCaptor.forClass(ConsentRecord.class);
        verify(repository, times(3)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(ConsentRecord::getConsentType)
                .containsExactly(ConsentType.TERMS, ConsentType.PRIVACY, ConsentType.AGE_OVER_14);
        assertThat(captor.getAllValues()).extracting(ConsentRecord::getAgreedAt).containsExactly(NOW, NOW, ageAt);
        assertThat(captor.getAllValues()).allMatch(r -> r.getUserId().equals(userId) && r.isActive());
    }
}
