package com.fashionapp.domain.purchase;

import com.fashionapp.domain.clothes.ClothesResponse;
import com.fasterxml.jackson.annotation.JsonInclude;

/** FR-12 decision result: the updated record, and for BUY the clothes created from it (null for SKIP). */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PurchaseDecisionResponse(PurchaseRecordResponse check, ClothesResponse clothes) {
}
