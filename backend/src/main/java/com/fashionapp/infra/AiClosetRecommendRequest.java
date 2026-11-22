package com.fashionapp.infra;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AiClosetRecommendRequest {
    private AiSituationRecommendRequest.WeatherInfo weather;
    private String situation;
    private AiSituationRecommendRequest.BodyProfile bodyProfile;
    private List<ClosetItem> clothes;
    // FR-28: valid combos chosen by the rules (the AI picks among them) and the rules applied ("TEMP_RANGE", "RAIN").
    // null when the forecast is unavailable — the AI then combines freely from clothes as before.
    private List<Candidate> candidates;
    private List<String> appliedRules;

    public AiClosetRecommendRequest(AiSituationRecommendRequest.WeatherInfo weather, String situation,
                                    AiSituationRecommendRequest.BodyProfile bodyProfile, List<ClosetItem> clothes) {
        this(weather, situation, bodyProfile, clothes, null, null);
    }

    @Getter
    @AllArgsConstructor
    public static class ClosetItem {
        private String id;
        private String category;
        private String color;
        private String name;
    }

    @Getter
    @AllArgsConstructor
    public static class Candidate {
        private List<String> clothesIds;
    }
}
