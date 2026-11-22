package com.fashionapp.domain.purchase;

import com.fashionapp.domain.clothes.ClothesCategory;
import com.fashionapp.infra.ImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseTempPhotoCleanerTest {

    private static final Instant NOW = Instant.parse("2026-11-22T03:00:00Z");
    private static final PurchaseCheckResponse.Item ITEM = new PurchaseCheckResponse.Item(ClothesCategory.TOP,
            "블랙", "블랙 hoodie", null, null, null, null);

    @Mock private PurchaseCheckRepository repository;
    @Mock private PurchaseDraftStore draftStore;
    @Mock private ImageStorage imageStorage;

    private final UUID userId = UUID.randomUUID();
    private PurchaseTempPhotoCleaner cleaner;

    @BeforeEach
    void setUp() {
        cleaner = new PurchaseTempPhotoCleaner(repository, draftStore, imageStorage,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private PurchaseCheck check() {
        UUID id = UUID.randomUUID();
        return new PurchaseCheck(id, userId, ITEM, PurchaseVerdict.BUY, 5, null, "tmp/" + userId + "/" + id + ".jpg",
                NOW.minusSeconds(3600));
    }

    @Test
    @DisplayName("30분 지난 임시 사진을 지우고 기록의 키를 비운다 (결정은 UNDECIDED 그대로)")
    void deletesPhotosOlderThanThirtyMinutes() {
        PurchaseCheck old = check();
        String key = old.getTempPhotoKey();
        given(repository.findWithTempPhotoCreatedBefore(eq(NOW.minus(PurchaseCheck.PHOTO_TTL)), any()))
                .willReturn(List.of(old));

        cleaner.deleteExpiredPhotos();

        verify(imageStorage).delete(key);
        verify(repository).save(old);
        verify(draftStore).delete(List.of(old.getId()));
        assertThat(old.getTempPhotoKey()).isNull();
        assertThat(old.decisionValue()).isEqualTo(PurchaseDecision.UNDECIDED);
    }

    @Test
    @DisplayName("삭제가 실패한 사진은 키를 남겨 다음 실행에서 다시 지운다")
    void keepsKeyWhenDeleteFails() {
        PurchaseCheck old = check();
        given(repository.findWithTempPhotoCreatedBefore(any(), any())).willReturn(List.of(old));
        willThrow(new IllegalStateException("io")).given(imageStorage).delete(any());

        cleaner.deleteExpiredPhotos();

        assertThat(old.getTempPhotoKey()).isNotNull();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("탈퇴: 기록을 지우고, 사용자 tmp/ 폴더 전체와 임시 속성을 지운다")
    void accountDeletion() {
        List<UUID> pending = List.of(UUID.randomUUID());
        given(repository.findIdsWithTempPhoto(userId)).willReturn(pending);

        assertThat(cleaner.deleteRecords(userId)).isEqualTo(pending);
        verify(repository).deleteAllByUserId(userId);

        cleaner.deleteFiles(userId, pending);
        verify(draftStore).delete(pending);
        verify(imageStorage).deleteByPrefix("tmp/" + userId + "/");
    }
}
