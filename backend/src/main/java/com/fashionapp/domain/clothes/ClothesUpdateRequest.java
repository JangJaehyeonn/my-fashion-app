package com.fashionapp.domain.clothes;

import lombok.Getter;

@Getter
public class ClothesUpdateRequest {
    private String category;
    private String color;
    private String pattern;
    private String season;
    private String styleTag;
}
