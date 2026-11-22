package com.fashionapp.domain.purchase;

import com.fashionapp.infra.ImageKeys;
import com.fashionapp.infra.ImageStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * FR-12: deletes purchase-check photos older than 30 minutes (every minute) and, on account deletion, every record,
 * temporary photo and draft of the user.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseTempPhotoCleaner {

    static final int BATCH_SIZE = 100;
    static final int MAX_BATCHES_PER_RUN = 10;

    private final PurchaseCheckRepository repository;
    private final PurchaseDraftStore draftStore;
    private final ImageStorage imageStorage;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${wearon.purchase-check.cleanup-delay:60s}",
            initialDelayString = "${wearon.purchase-check.cleanup-delay:60s}")
    public void deleteExpiredPhotos() {
        Instant cutoff = clock.instant().minus(PurchaseCheck.PHOTO_TTL);
        int deleted = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            List<PurchaseCheck> expired = repository.findWithTempPhotoCreatedBefore(cutoff, PageRequest.of(0, BATCH_SIZE));
            int removed = expired.stream().mapToInt(check -> removePhoto(check) ? 1 : 0).sum();
            deleted += removed;
            // Stop when done, or when nothing could be removed (storage down) so the same rows are not retried in a loop
            if (expired.size() < BATCH_SIZE || removed == 0) {
                break;
            }
        }
        if (deleted > 0) {
            log.info("purchase.cleanup tempPhotos={}", deleted);
        }
    }

    /** Account deletion, inside its DB transaction: drops the records. Returns ids whose photo/draft may remain. */
    public List<UUID> deleteRecords(UUID userId) {
        List<UUID> pending = repository.findIdsWithTempPhoto(userId);
        repository.deleteAllByUserId(userId);
        return pending;
    }

    /** Account deletion, after commit: every temporary photo of the user and the drafts. Throws on storage failure. */
    public void deleteFiles(UUID userId, List<UUID> pendingIds) {
        draftStore.delete(pendingIds);
        imageStorage.deleteByPrefix(ImageKeys.tempPrefix(userId));
    }

    private boolean removePhoto(PurchaseCheck check) {
        try {
            imageStorage.delete(check.getTempPhotoKey());
        } catch (RuntimeException e) {
            log.warn("purchase.cleanup temp photo delete failed: {}", e.getClass().getSimpleName());
            return false;
        }
        check.photoRemoved();
        repository.save(check);
        draftStore.delete(List.of(check.getId()));
        return true;
    }
}
