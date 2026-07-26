package com.fashionapp.domain.outfit;

import com.fashionapp.domain.user.User;
import com.fashionapp.domain.user.UserRepository;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.infra.AiServerClient;
import com.fashionapp.infra.AiSituationRecommendRequest;
import com.fashionapp.infra.AiSituationRecommendResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutfitService {

    private final UserRepository userRepository;
    private final AiServerClient aiServerClient;

    @Transactional(readOnly = true)
    public AiSituationRecommendResponse recommendBySituation(UUID userId, SituationRecommendRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        AiSituationRecommendRequest.BodyProfile bodyProfile = new AiSituationRecommendRequest.BodyProfile(
                user.getHeight(),
                user.getWeight(),
                user.getBodyType() != null ? user.getBodyType().name() : null,
                user.getPreferredStyle() != null ? user.getPreferredStyle().name() : null
        );

        AiSituationRecommendRequest aiRequest = new AiSituationRecommendRequest(
                new AiSituationRecommendRequest.WeatherInfo(request.getTemperature(), request.getCondition()),
                request.getSituation(),
                bodyProfile
        );

        return aiServerClient.recommendOutfitsBySituation(aiRequest);
    }
}
