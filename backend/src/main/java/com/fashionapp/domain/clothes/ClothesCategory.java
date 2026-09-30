package com.fashionapp.domain.clothes;

public enum ClothesCategory {
    TOP, BOTTOM, SHOES, OUTER, ETC;

    // DB에는 문자열로 저장 — 피봇 이전 옛 clothes 행(자유 텍스트 category)이 남아 있어도 조회가 깨지지 않도록 ETC로 흡수
    public static ClothesCategory from(String value) {
        if (value == null) {
            return ETC;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ETC;
        }
    }
}
