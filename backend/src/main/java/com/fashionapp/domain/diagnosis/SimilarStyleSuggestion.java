package com.fashionapp.domain.diagnosis;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SimilarStyleSuggestion {
    private String styleTag;
    private String description;
}
