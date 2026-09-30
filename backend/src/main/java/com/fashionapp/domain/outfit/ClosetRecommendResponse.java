package com.fashionapp.domain.outfit;

import com.fashionapp.domain.clothes.ClothesResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ClosetRecommendResponse {
    private List<ClosetOutfit> outfits;

    @Getter
    @AllArgsConstructor
    public static class ClosetOutfit {
        // 카드에 사진을 바로 그릴 수 있도록 ID가 아니라 presigned URL이 포함된 옷 정보 자체를 내려줌
        private List<ClothesResponse> items;
        private String reason;
        private String styleTag;
    }
}
