package com.fashionapp.domain.clothes;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class ClothesResponse {

    private UUID id;
    private String imageUrl;
    private ClothesCategory category;
    private String color;
    private String name;
    // true when the category is UNCLASSIFIED (AI classification failed and the user has not fixed it yet)
    private boolean classificationFailed;
    private LocalDateTime createdAt;
    // FR-07 attributes. Always present in JSON; null means unknown.
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String fit;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String material;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String pattern;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private ClothesSeason season;
    // FR-09: valid combos this piece is in (0 for unclassified/ETC). Filled in the clothes list; null elsewhere.
    private Integer comboCount;
    // FR-10: days this piece was worn and the last of them ("YYYY-MM-DD", null if never). Filled in the clothes list; null elsewhere.
    private Integer wearCount;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private LocalDate lastWornAt;
    // FR-27: effective status (LAUNDRY past its days reads AVAILABLE) and "can wear now"
    // (AVAILABLE, not on wear cooldown, classified). Pieces just registered have no wears, so no cooldown.
    private ClothesStatus status;
    private boolean available;
}
