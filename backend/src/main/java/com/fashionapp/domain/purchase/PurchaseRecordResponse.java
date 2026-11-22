package com.fashionapp.domain.purchase;

import com.fashionapp.domain.clothes.ClothesCategory;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * FR-12 purchase check history entry. imageUrl is set only for BOUGHT checks (the clothes photo); price is null when
 * none was given.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PurchaseRecordResponse(UUID id, String name, ClothesCategory category, String color, Long price,
                                     PurchaseVerdict verdict, Integer plus, PurchaseDecision decision,
                                     UUID clothesId, String imageUrl, Instant createdAt) {
}
