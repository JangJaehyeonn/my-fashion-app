package com.fashionapp.domain.clothes;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * FR-27 PATCH /api/clothes/status. Values are strings so a malformed id or status becomes a 400 from ClothesService
 * instead of a JSON error.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ClothesStatusRequest {
    private List<String> clothesIds;
    private String status;
}
