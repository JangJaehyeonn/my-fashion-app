package com.fashionapp.domain.clothes;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** FR-27: the pieces changed, their new status and, for LAUNDRY, when they count as AVAILABLE again. */
public record ClothesStatusResponse(List<UUID> clothesIds, ClothesStatus status,
                                    @JsonInclude(JsonInclude.Include.ALWAYS) LocalDateTime statusUntil) {
}
