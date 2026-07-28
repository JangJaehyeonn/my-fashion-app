package com.fashionapp.domain.home;

import com.fashionapp.domain.user.User;
import com.fashionapp.domain.user.UserRepository;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.infra.AiServerClient;
import com.fashionapp.infra.AiShoppingRecommendRequest;
import com.fashionapp.infra.AiShoppingRecommendResponse;
import com.fashionapp.infra.AiSituationRecommendRequest;
import com.fashionapp.infra.AiSituationRecommendResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Service
@RequiredArgsConstructor
public class HomeService {

    private final UserRepository userRepository;
    private final AiServerClient aiServerClient;

    @Transactional(readOnly = true)
    public HomeSummaryResponse getSummary(UUID userId, HomeSummaryRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String bodyType = user.getBodyType() != null ? user.getBodyType().name() : null;
        String preferredStyle = user.getPreferredStyle() != null ? user.getPreferredStyle().name() : null;

        AiSituationRecommendRequest outfitRequest = new AiSituationRecommendRequest(
                new AiSituationRecommendRequest.WeatherInfo(request.getTemperature(), request.getCondition()),
                request.getSituation(),
                new AiSituationRecommendRequest.BodyProfile(user.getHeight(), user.getWeight(), bodyType, preferredStyle)
        );

        AiShoppingRecommendRequest shoppingRequest = new AiShoppingRecommendRequest(
                request.getBudget(),
                request.getSituation(),
                new AiShoppingRecommendRequest.BodyProfile(user.getHeight(), user.getWeight(), bodyType, preferredStyle)
        );

        CompletableFuture<AiSituationRecommendResponse> outfitFuture = aiServerClient.recommendOutfitsBySituation(outfitRequest);
        CompletableFuture<AiShoppingRecommendResponse> shoppingFuture = aiServerClient.recommendShopping(shoppingRequest);

        try {
            CompletableFuture.allOf(outfitFuture, shoppingFuture).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof CustomException customException) {
                throw customException;
            }
            throw e;
        }

        return new HomeSummaryResponse(outfitFuture.join(), shoppingFuture.join());
    }
}
