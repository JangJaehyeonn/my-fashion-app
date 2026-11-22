package com.fashionapp.domain.purchase;

import java.util.OptionalDouble;

/**
 * FR-11 verdict rule. SIMILAR when the most similar closet piece reaches {@code similarThreshold}, otherwise BUY when
 * +N reaches {@code buyMinPlus}, otherwise THINK. Pure: the AI never takes part in it.
 *
 * @param similarThreshold cosine similarity at or above which a closet piece counts as similar
 *                         (wearon.purchase-check.similar-threshold, default 0.85)
 * @param buyMinPlus       smallest +N that gives BUY (wearon.purchase-check.buy-min-plus, default 5)
 */
public record PurchaseCheckRules(double similarThreshold, int buyMinPlus) {

    /**
     * Similarity comes back from pgvector as 1 - cosine distance in floating point, so a value that is exactly the
     * threshold mathematically (e.g. 0.85) may arrive as 0.84999999. Values this close to the threshold count as equal.
     */
    static final double SIMILARITY_TOLERANCE = 1e-6;

    public PurchaseCheckRules {
        if (!(similarThreshold > 0 && similarThreshold <= 1)) {
            throw new IllegalArgumentException("similar-threshold must be in (0, 1]: " + similarThreshold);
        }
        if (buyMinPlus < 0) {
            throw new IllegalArgumentException("buy-min-plus must not be negative: " + buyMinPlus);
        }
    }

    public boolean isSimilar(double similarity) {
        return similarity + SIMILARITY_TOLERANCE >= similarThreshold;
    }

    /** @param topSimilarity similarity of the most similar closet piece of the same category (empty when none) */
    public PurchaseVerdict verdict(OptionalDouble topSimilarity, int plus) {
        if (topSimilarity.isPresent() && isSimilar(topSimilarity.getAsDouble())) {
            return PurchaseVerdict.SIMILAR;
        }
        return plus >= buyMinPlus ? PurchaseVerdict.BUY : PurchaseVerdict.THINK;
    }
}
