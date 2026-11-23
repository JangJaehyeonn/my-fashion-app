package com.fashionapp.domain.wear;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * FR-10 GET /api/wears/summary. Counts are wear records (one per piece per day) from the start of this week (Monday)
 * or month through today. {@code mostWorn} is the piece with the most worn days overall; null when nothing is recorded.
 * {@code mostWorn.count} is its worn days overall (FR-20 home card).
 */
public record WearSummaryResponse(
        long weekCount,
        long monthCount,
        @JsonInclude(JsonInclude.Include.ALWAYS) MostWorn mostWorn) {

    public record MostWorn(UUID id, String name, long count) {
    }
}
