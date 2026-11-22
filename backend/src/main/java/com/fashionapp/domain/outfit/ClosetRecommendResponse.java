package com.fashionapp.domain.outfit;

import com.fashionapp.domain.clothes.ClothesResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

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
        // FR-29: true on the combo that brings back a long-unworn piece, which is revivedClothesId (null otherwise)
        private boolean revived;
        private UUID revivedClothesId;

        public ClosetOutfit(List<ClothesResponse> items, String reason, String styleTag) {
            this(items, reason, styleTag, false, null);
        }
    }
}
