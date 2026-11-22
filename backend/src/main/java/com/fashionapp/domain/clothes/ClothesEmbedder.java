package com.fashionapp.domain.clothes;

import com.fashionapp.infra.AiEmbedResponse;
import com.fashionapp.infra.AiServerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
