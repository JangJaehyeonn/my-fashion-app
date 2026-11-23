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
    // Rule-made valid combos (FR-29: a revived combo comes first). The AI picks among them.
    private List<Candidate> candidates;
    // FR-28: weather rules that narrowed the candidates ("TEMP_RANGE", "RAIN"). null when there is no forecast.
    private List<String> appliedRules;

    public AiClosetRecommendRequest(AiSituationRecommendRequest.WeatherInfo weather, String situation,
                                    AiSituationRecommendRequest.BodyProfile bodyProfile, List<ClosetItem> clothes,
                                    List<Candidate> candidates) {
        this(weather, situation, bodyProfile, clothes, candidates, null);
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
