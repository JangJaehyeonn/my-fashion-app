package com.fashionapp.infra;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AiClothesClassifyResponse {
    private String category;
    private String color;
    private String name;
    // FR-07 attributes. null (or missing) means unknown.
    private String fit;
    private String material;
    private String pattern;
    private String season;
}
