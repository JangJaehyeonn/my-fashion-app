package com.fashionapp.domain.diagnosis;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class StyleDiagnosisResponse {

    private UUID id;
    private String imageUrl;
    private Integer score;
    private String feedback;
    private List<SimilarStyleSuggestion> similarStyles;
    private LocalDateTime createdAt;
}
