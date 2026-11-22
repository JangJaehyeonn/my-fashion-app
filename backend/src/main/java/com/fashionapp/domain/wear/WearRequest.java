package com.fashionapp.domain.wear;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * POST /api/wears. Values are strings so a malformed id or date becomes a 400 from WearService instead of a JSON error.
 * {@code date} is "YYYY-MM-DD"; omitted means today.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class WearRequest {
    private List<String> clothesIds;
    private String date;
}
