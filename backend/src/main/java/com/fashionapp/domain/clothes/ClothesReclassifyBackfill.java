package com.fashionapp.domain.clothes;

import com.fashionapp.infra.ImageKeys;
import com.fashionapp.infra.ImageStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * FR-08: re-classifies the photo of clothes that have none of the FR-07 attributes (fit, material, pattern, season)
 * and fills the empty fields, then recomputes the embedding.
 *
 * <p>Only active with {@code wearon.backfill.reclassify=true} (default false): it calls the paid classify API once
 * per item. Runs on a background thread; stops after {@link #MAX_CONSECUTIVE_FAILURES} classify failures in a row.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "wearon.backfill", name = "reclassify", havingValue = "true")
@Order(ClothesReclassifyBackfill.RUNNER_ORDER)
public class ClothesReclassifyBackfill implements ApplicationRunner {

    static final int RUNNER_ORDER = ClothesEmbeddingStore.RUNNER_ORDER + 10;
    static final int PAGE_SIZE = 20;
    static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final UUID MIN_ID = new UUID(0L, 0L);

    private final ClothesRepository clothesRepository;
    private final ClothesClassifier clothesClassifier;
    private final ImageStorage imageStorage;
    private final ClothesEmbedder clothesEmbedder;
    private final Executor executor;

    @Autowired
    public ClothesReclassifyBackfill(ClothesRepository clothesRepository, ClothesClassifier clothesClassifier,
                                     ImageStorage imageStorage, ClothesEmbedder clothesEmbedder) {
        this(clothesRepository, clothesClassifier, imageStorage, clothesEmbedder,
                BackgroundStarter.daemon("clothes-reclassify-backfill"));
    }

    ClothesReclassifyBackfill(ClothesRepository clothesRepository, ClothesClassifier clothesClassifier,
                              ImageStorage imageStorage, ClothesEmbedder clothesEmbedder, Executor executor) {
        this.clothesRepository = clothesRepository;
        this.clothesClassifier = clothesClassifier;
        this.imageStorage = imageStorage;
        this.clothesEmbedder = clothesEmbedder;
        this.executor = executor;
    }

    @Override
    public void run(ApplicationArguments args) {
        executor.execute(this::backfillSafely);
    }

    private void backfillSafely() {
        try {
            backfill();
        } catch (RuntimeException e) {
            log.warn("clothes reclassify backfill stopped: {}", e.getClass().getSimpleName());
        }
    }

    /** Returns how many clothes were re-classified. */
    int backfill() {
        UUID after = MIN_ID;
        int updated = 0;
        int failuresInRow = 0;
        while (true) {
            List<Clothes> page = clothesRepository.findMissingAttributesAfter(after, PageRequest.of(0, PAGE_SIZE));
            if (page.isEmpty()) {
                break;
            }
            for (Clothes clothes : page) {
                after = clothes.getId();
                Outcome outcome = reclassify(clothes);
                if (outcome != Outcome.FAILED) {
                    updated += outcome == Outcome.UPDATED ? 1 : 0;
                    failuresInRow = 0;
                } else if (++failuresInRow >= MAX_CONSECUTIVE_FAILURES) {
                    log.warn("clothes reclassify backfill paused after {} updated: classify unavailable", updated);
                    return updated;
                }
            }
        }
        log.info("clothes reclassify backfill: updated={}", updated);
        return updated;
    }

    private enum Outcome { UPDATED, SKIPPED, FAILED }

    private Outcome reclassify(Clothes clothes) {
        String key = ImageKeys.toKey(clothes.getImageUrl());
        Optional<byte[]> bytes = imageStorage.readBytes(key);
        if (bytes.isEmpty()) {
            log.warn("clothes reclassify backfill: image missing for {}", clothes.getId());
            return Outcome.SKIPPED; // not an AI failure
        }
        Optional<ClothesClassification> classification = clothesClassifier
                .classify(bytes.get(), key.substring(key.lastIndexOf('/') + 1), ImageKeys.contentTypeOf(key));
        if (classification.isEmpty()) {
            return Outcome.FAILED;
        }
        // The user may have edited or deleted the item while the AI was working: re-read before writing.
        clothesRepository.findById(clothes.getId()).ifPresent(current -> {
            current.fillMissingFrom(classification.get());
            clothesEmbedder.refresh(clothesRepository.save(current));
        });
        return Outcome.UPDATED;
    }
}
