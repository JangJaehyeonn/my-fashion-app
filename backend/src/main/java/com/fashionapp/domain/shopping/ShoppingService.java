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
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShoppingService {

    private final UserRepository userRepository;
    private final AiServerClient aiServerClient;

    @Transactional(readOnly = true)
    public AiShoppingRecommendResponse recommend(UUID userId, ShoppingRecommendRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        AiShoppingRecommendRequest.BodyProfile bodyProfile = new AiShoppingRecommendRequest.BodyProfile(
                user.getHeight(),
                user.getWeight(),
                user.getBodyType() != null ? user.getBodyType().name() : null,
                user.getPreferredStyle() != null ? user.getPreferredStyle().name() : null
        );

        AiShoppingRecommendRequest aiRequest = new AiShoppingRecommendRequest(
                request.getBudget(),
                request.getSituation(),
                bodyProfile
        );

        return aiServerClient.recommendShopping(aiRequest);
    }
}
