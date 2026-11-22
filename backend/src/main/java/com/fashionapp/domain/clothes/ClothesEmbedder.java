package com.fashionapp.domain.clothes;

import com.fashionapp.infra.AiEmbedBatchResponse;
import com.fashionapp.infra.AiEmbedResponse;
import com.fashionapp.infra.AiServerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Computes and stores a clothes embedding (FR-07). Never throws: an AI or DB failure leaves the embedding empty
 * so registration, edit and reclassification still succeed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClothesEmbedder {

    private static final Map<ClothesCategory, String> CATEGORY_LABELS = Map.of(
            ClothesCategory.TOP, "상의",
            ClothesCategory.BOTTOM, "하의",
            ClothesCategory.OUTER, "아우터",
            ClothesCategory.SHOES, "신발",
            ClothesCategory.ETC, "기타");

    /** AI server limit for one {"texts": [...]} request. */
    public static final int MAX_BATCH_SIZE = 100;

    private final AiServerClient aiServerClient;
    private final ClothesEmbeddingStore embeddingStore;

    /** Recomputes the embedding from the current attributes. Unclassified clothes get no embedding. */
    public void refresh(Clothes clothes) {
        if (clothes.getId() == null || !embeddingStore.isAvailable()) {
            return;
        }
        Optional<String> text = embeddingText(clothes);
        Optional<float[]> vector = text.flatMap(this::embed);
        if (vector.isEmpty() || !embeddingStore.save(clothes.getId(), vector.get())) {
            embeddingStore.clear(clothes.getId());
        }
    }

    /** Outcome of {@link #fillBatch}: how many clothes had an embedding text and how many vectors were stored. */
    public record BatchResult(int attempted, int saved) {
        /** True when there was something to embed but nothing could be stored (AI or DB down). */
        public boolean failed() {
            return attempted > 0 && saved == 0;
        }
    }

    /**
     * FR-08: embeds clothes that have no embedding yet with one batch AI call (at most {@link #MAX_BATCH_SIZE}).
     * Never throws. Unclassified clothes are skipped; an AI failure stores nothing and leaves embeddings empty.
     */
    public BatchResult fillBatch(List<Clothes> batch) {
        List<Clothes> targets = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (Clothes clothes : batch) {
            Optional<String> text = clothes.getId() == null ? Optional.empty() : embeddingText(clothes);
            text.filter(t -> !t.isEmpty()).ifPresent(t -> {
                targets.add(clothes);
                texts.add(t);
            });
        }
        if (targets.isEmpty() || !embeddingStore.isAvailable()) {
            return new BatchResult(targets.size(), 0);
        }
        List<Optional<float[]>> vectors = embedBatch(texts);
        int saved = 0;
        for (int i = 0; i < vectors.size(); i++) {
            Optional<float[]> vector = vectors.get(i);
            if (vector.isPresent() && embeddingStore.save(targets.get(i).getId(), vector.get())) {
                saved++;
            }
        }
        return new BatchResult(targets.size(), saved);
    }

    /** One entry per text; all empty when the call fails or the response does not match the request. */
    private List<Optional<float[]>> embedBatch(List<String> texts) {
        if (texts.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("batch larger than " + MAX_BATCH_SIZE);
        }
        try {
            AiEmbedBatchResponse response = aiServerClient.embedBatch(texts);
            List<List<Double>> embeddings = response == null ? null : response.getEmbeddings();
            if (embeddings == null || embeddings.size() != texts.size()) {
                log.warn("AI embed batch returned a malformed response");
                return List.of();
            }
            return embeddings.stream().map(ClothesEmbedder::toVector).toList();
        } catch (RuntimeException e) {
            // AiServerClient already logs the cause (timeout / server error)
            log.warn("AI embed batch unavailable: {}", e.getClass().getSimpleName());
            return List.of();
        }
    }

    /** '카테고리 색상 핏 소재 패턴 이름' with nulls left out. Empty for unclassified clothes. */
    static Optional<String> embeddingText(Clothes clothes) {
        ClothesCategory category = ClothesCategory.from(clothes.getCategory());
        if (category == ClothesCategory.UNCLASSIFIED) {
            return Optional.empty();
        }
        String text = Stream.of(CATEGORY_LABELS.get(category), clothes.getColor(), clothes.getFit(),
                        clothes.getMaterial(), clothes.getPattern(), clothes.getName())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" "));
        return Optional.of(text);
    }

    private Optional<float[]> embed(String text) {
        try {
            AiEmbedResponse response = aiServerClient.embed(text);
            Optional<float[]> vector = toVector(response == null ? null : response.getEmbedding());
            if (vector.isEmpty()) {
                log.warn("AI embed returned a malformed response");
            }
            return vector;
        } catch (RuntimeException e) {
            // AiServerClient already logs the cause (timeout / server error)
            log.warn("AI embed unavailable: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** Accepts exactly DIMENSIONS finite numbers. */
    static Optional<float[]> toVector(List<Double> values) {
        if (values == null || values.size() != ClothesEmbeddingStore.DIMENSIONS) {
            return Optional.empty();
        }
        float[] vector = new float[values.size()];
        for (int i = 0; i < vector.length; i++) {
            Double value = values.get(i);
            if (value == null || !Double.isFinite(value)) {
                return Optional.empty();
            }
            vector[i] = value.floatValue();
        }
        return Optional.of(vector);
    }
}
