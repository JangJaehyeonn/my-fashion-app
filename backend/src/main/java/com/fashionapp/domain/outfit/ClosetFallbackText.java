package com.fashionapp.domain.outfit;

import java.util.ArrayList;
import java.util.List;

/** FR-28 rule-based sentence for outfits picked by the rules when the AI chose none of the candidates. */
final class ClosetFallbackText {

    static final String STYLE_TAG = "데일리";

    private ClosetFallbackText() {
    }

    static String reason(List<String> appliedRules) {
        List<String> parts = new ArrayList<>();
        if (appliedRules.contains(WeatherOutfitPlanner.TEMP_RANGE)) {
            parts.add("일교차가 커서 외투를 벗어도 어울리는 조합으로 골랐어요.");
        }
        if (appliedRules.contains(WeatherOutfitPlanner.RAIN)) {
            parts.add("비 소식이 있어 젖기 쉬운 스웨이드와 흰 신발은 뺐어요.");
        }
        if (parts.isEmpty()) {
            parts.add("내 옷장에서 서로 잘 어울리는 조합으로 골랐어요.");
        }
        return String.join(" ", parts);
    }
}
