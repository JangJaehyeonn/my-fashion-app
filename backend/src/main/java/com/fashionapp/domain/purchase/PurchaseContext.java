package com.fashionapp.domain.purchase;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the rules decided for one purchase check, handed to {@link PurchaseExplainer} for the sentences.
 *
 * @param similar          most similar closet pieces of the same category (notes not filled yet)
 * @param distribution     closet piece count per classified category name
 * @param candidates       valid combos with the new piece, closet clothes ids only, in engine order
 * @param sameCategoryCount closet pieces in the new piece's category
 * @param pieceLabels      short "카테고리 색 이름" text per closet clothes id, for the AI prompt
 */
public record PurchaseContext(PurchaseCheckResponse.Item item, PurchaseVerdict verdict, int plus,
                              int comboCountBefore, int comboCountAfter, List<PurchaseCheckResponse.Similar> similar,
                              Map<String, Integer> distribution, List<List<UUID>> candidates,
                              int sameCategoryCount, Map<UUID, String> pieceLabels) {

    public PurchaseContext {
        similar = List.copyOf(similar);
        distribution = Map.copyOf(distribution);
        candidates = candidates.stream().map(List::copyOf).toList();
        pieceLabels = Map.copyOf(pieceLabels);
    }
}
