package com.fashionapp.domain.home;

import com.fashionapp.domain.user.User;
import com.fashionapp.domain.user.UserRepository;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.infra.AiServerClient;
import com.fashionapp.infra.AiSituationRecommendRequest;
import com.fashionapp.infra.AiSituationRecommendResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.List;
import java.util.concurrent.CompletionException;

@Service
@RequiredArgsConstructor
public class HomeService {

    private final UserRepository userRepository;
    private final AiServerClient aiServerClient;

    // AI 서버 호출(수 초~수 분)을 기다리는 동안 DB 커넥션을 붙잡지 않도록 트랜잭션을 걸지 않음 —
    // 조회·저장은 각 리포지토리 호출이 짧은 트랜잭션으로 처리 (open-in-view도 꺼져 있어야 함, application.yml)
    public HomeSummaryResponse getSummary(UUID userId, HomeSummaryRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String bodyType = user.getBodyType() != null ? user.getBodyType().name() : null;
        List<String> preferredStyles = user.getPreferredStyleNames();
        String preferredStyle = preferredStyles.stream().findFirst().orElse(null);

        AiSituationRecommendRequest outfitRequest = new AiSituationRecommendRequest(
                new AiSituationRecommendRequest.WeatherInfo(request.getTemperature(), request.getCondition()),
                request.getSituation(),
                new AiSituationRecommendRequest.BodyProfile(user.getHeight(), user.getWeight(), bodyType, preferredStyle, preferredStyles)
        );

        try {
            return new HomeSummaryResponse(aiServerClient.recommendOutfitsBySituation(outfitRequest).join());
        } catch (CompletionException e) {
            if (e.getCause() instanceof CustomException customException) {
                throw customException;
            }
            throw e;
        }
    }
}
