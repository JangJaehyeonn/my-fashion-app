package com.fashionapp.domain.shopping;

import com.fashionapp.domain.user.User;
import com.fashionapp.domain.user.UserRepository;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.infra.AiServerClient;
import com.fashionapp.infra.AiShoppingRecommendRequest;
import com.fashionapp.infra.AiShoppingRecommendResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletionException;

@Service
@RequiredArgsConstructor
public class ShoppingService {

    private final UserRepository userRepository;
    private final AiServerClient aiServerClient;

    // AI 서버 호출(수 초~수 분)을 기다리는 동안 DB 커넥션을 붙잡지 않도록 트랜잭션을 걸지 않음 —
    // 조회·저장은 각 리포지토리 호출이 짧은 트랜잭션으로 처리 (open-in-view도 꺼져 있어야 함, application.yml)
    public AiShoppingRecommendResponse recommend(UUID userId, ShoppingRecommendRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        AiShoppingRecommendRequest.BodyProfile bodyProfile = new AiShoppingRecommendRequest.BodyProfile(
                user.getHeight(),
                user.getWeight(),
                user.getBodyType() != null ? user.getBodyType().name() : null,
                user.getPreferredStyleNames().stream().findFirst().orElse(null),
                user.getPreferredStyleNames()
        );

        AiShoppingRecommendRequest aiRequest = new AiShoppingRecommendRequest(
                request.getBudget(),
                request.getSituation(),
                bodyProfile
        );

        try {
            return aiServerClient.recommendShopping(aiRequest).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof CustomException customException) {
                throw customException;
            }
            throw e;
        }
    }
}
