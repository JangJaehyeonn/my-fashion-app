package com.fashionapp.domain.purchase;

/**
 * FR-20 GET /api/purchase-checks/summary: purchase checks made this month (Korean calendar) and the combos added by the
 * pieces bought from them (sum of their plus).
 */
public record PurchaseSummaryResponse(long monthCount, long monthPlus) {
}
