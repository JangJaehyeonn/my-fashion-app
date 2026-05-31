package com.fashionapp.domain.outfit;

import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
public class OutfitCreateRequest {
    private String name;
    private String styleTag;
    private String weatherCondition;
    private List<UUID> clothesIds;
}
