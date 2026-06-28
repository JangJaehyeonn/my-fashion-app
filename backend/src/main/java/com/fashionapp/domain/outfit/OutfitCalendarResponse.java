package com.fashionapp.domain.outfit;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
public class OutfitCalendarResponse {

    private UUID id;
    private UUID outfitId;
    private String outfitName;
    private LocalDate wornDate;
    private String memo;

    public static OutfitCalendarResponse from(OutfitCalendar calendar) {
        return OutfitCalendarResponse.builder()
                .id(calendar.getId())
                .outfitId(calendar.getOutfit().getId())
                .outfitName(calendar.getOutfit().getName())
                .wornDate(calendar.getWornDate())
                .memo(calendar.getMemo())
                .build();
    }
}
