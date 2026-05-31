package com.fashionapp.domain.outfit;

import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
public class OutfitCalendarCreateRequest {
    private UUID outfitId;
    private LocalDate wornDate;
    private String memo;
}
