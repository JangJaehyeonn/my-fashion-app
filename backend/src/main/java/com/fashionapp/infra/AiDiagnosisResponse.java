package com.fashionapp.infra;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class AiDiagnosisResponse {
    private Integer score;
    private String feedback;
    private List<SimilarStyleSuggestion> similarStyles;

    @Getter
    @NoArgsConstructor
    public static class SimilarStyleSuggestion {
        private String styleTag;
        private String description;
    }
}
