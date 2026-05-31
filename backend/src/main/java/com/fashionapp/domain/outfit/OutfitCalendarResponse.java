package com.fashionapp.domain.outfit;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class OutfitCalendarResponse {

    private UUID id;
    private OutfitResponse outfit;
    private LocalDate wornDate;
    private String memo;
    private LocalDateTime createdAt;

    public static OutfitCalendarResponse from(OutfitCalendar calendar) {
        return OutfitCalendarResponse.builder()
                .id(calendar.getId())
                .outfit(OutfitResponse.from(calendar.getOutfit()))
                .wornDate(calendar.getWornDate())
                .memo(calendar.getMemo())
                .createdAt(calendar.getCreatedAt())
                .build();
    }
}
