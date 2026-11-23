package com.fashionapp.domain.purchase;

import com.fashionapp.domain.clothes.ClothesCategory;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * FR-12 purchase check history entry. imageUrl is set only for BOUGHT checks (the clothes photo); price is null when
 * none was given.
 *
 * <p>FR-20: expectedCostPerWear is the cost per wear estimated at check time (null when unknown). wearCount is how
 * often the bought piece was worn (null unless BOUGHT and the piece still exists) and currentCostPerWear is
 * price ÷ wearCount in won (null without a price or before the first wear).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PurchaseRecordResponse(UUID id, String name, ClothesCategory category, String color, Long price,
                                     PurchaseVerdict verdict, Integer plus, PurchaseDecision decision,
                                     UUID clothesId, String imageUrl, Instant createdAt,
                                     Long expectedCostPerWear, Long wearCount, Long currentCostPerWear) {
}
