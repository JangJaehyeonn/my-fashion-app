package com.fashionapp.infra;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AiClassifyResponse {
    private String category;
    private String color;
    private String pattern;
    private String season;
    private String styleTag;
}
