package com.fashionapp.domain.purchase;

/** FR-12: what the user did with a purchase check. Stored by name in purchase_checks.decision. */
public enum PurchaseDecision {
    BOUGHT,
    SKIPPED,
    UNDECIDED
}
