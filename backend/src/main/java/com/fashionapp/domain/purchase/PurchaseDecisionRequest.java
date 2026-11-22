package com.fashionapp.domain.purchase;

import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;

/** FR-12 POST /api/purchase-checks/{id}/decision body: {"decision": "BUY" | "SKIP"}. */
public record PurchaseDecisionRequest(String decision) {

    public enum Choice {
        BUY,
        SKIP
    }

    /** The parsed choice; 400 INVALID_PURCHASE_DECISION for anything else. */
    public Choice choice() {
        if (decision != null) {
            for (Choice choice : Choice.values()) {
                if (choice.name().equalsIgnoreCase(decision.trim())) {
                    return choice;
                }
            }
        }
        throw new CustomException(ErrorCode.INVALID_PURCHASE_DECISION);
    }
}
