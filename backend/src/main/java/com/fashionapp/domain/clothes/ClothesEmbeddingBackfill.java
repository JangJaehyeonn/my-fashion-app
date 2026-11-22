package com.fashionapp.domain.clothes;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * FR-08: on startup, fills embeddings of classified clothes that have none, in batches (one AI call per batch).
 *
 * <p>Runs on a background thread, so startup never waits for or fails because of the AI server. The first batch
 * that cannot store anything (AI down, bad response) stops the run; the next startup tries again.
 */
@Slf4j
@Component
@Order(ClothesEmbeddingBackfill.RUNNER_ORDER)
public class ClothesEmbeddingBackfill implements ApplicationRunner {

    /** After ClothesEmbeddingStore (availability) and ClothesReclassifyBackfill. */
    static final int RUNNER_ORDER = ClothesEmbeddingStore.RUNNER_ORDER + 20;
    static final int BATCH_SIZE = 50;

    private final ClothesEmbeddingStore embeddingStore;
    private final ClothesRepository clothesRepository;
    private final ClothesEmbedder clothesEmbedder;
    private final Executor executor;

    @Autowired
    public ClothesEmbeddingBackfill(ClothesEmbeddingStore embeddingStore, ClothesRepository clothesRepository,
                                    ClothesEmbedder clothesEmbedder) {
        this(embeddingStore, clothesRepository, clothesEmbedder, BackgroundStarter.daemon("clothes-embedding-backfill"));
    }

    ClothesEmbeddingBackfill(ClothesEmbeddingStore embeddingStore, ClothesRepository clothesRepository,
                             ClothesEmbedder clothesEmbedder, Executor executor) {
        this.embeddingStore = embeddingStore;
        this.clothesRepository = clothesRepository;
        this.clothesEmbedder = clothesEmbedder;
        this.executor = executor;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!embeddingStore.isAvailable()) {
            return;
        }
        executor.execute(this::backfillSafely);
    }

    private void backfillSafely() {
        try {
            backfill();
        } catch (RuntimeException e) {
            log.warn("clothes embedding backfill stopped: {}", e.getClass().getSimpleName());
        }
    }

    /** Returns how many embeddings were stored. */
    int backfill() {
        UUID after = null;
        int saved = 0;
        while (true) {
            List<UUID> ids = embeddingStore.findIdsWithoutEmbedding(after, BATCH_SIZE);
            if (ids.isEmpty()) {
                break;
            }
            after = ids.get(ids.size() - 1);
            ClothesEmbedder.BatchResult result = clothesEmbedder.fillBatch(clothesRepository.findAllById(ids));
            if (result.failed()) {
                log.warn("clothes embedding backfill paused after {} saved: embedding unavailable", saved);
                return saved;
            }
            saved += result.saved();
        }
        if (saved > 0) {
            log.info("clothes embedding backfill: saved={}", saved);
        }
        return saved;
    }
}
