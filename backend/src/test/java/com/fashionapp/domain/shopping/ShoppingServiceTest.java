package com.fashionapp.domain.shopping;

import com.fashionapp.domain.user.User;
import com.fashionapp.domain.user.UserRepository;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.infra.AiServerClient;
import com.fashionapp.infra.AiShoppingRecommendRequest;
import com.fashionapp.infra.AiShoppingRecommendResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * ShoppingService 단위 테스트 — 예산/상황 + 체형 프로필로 AI 서버(비동기) 호출을 위임하는 로직을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ShoppingServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AiServerClient aiServerClient;

    private ShoppingService shoppingService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        shoppingService = new ShoppingService(userRepository, aiServerClient);
        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .email("test@example.com")
                .nickname("테스터")
                .provider(User.AuthProvider.kakao)
                .providerId("kakao-1")
                .height(180)
                .weight(75)
                .bodyType(User.BodyType.NORMAL)
                .preferredStyle(User.PreferredStyle.STREET)
                .build();
    }

    private ShoppingRecommendRequest shoppingRequest(int budget, String situation) throws Exception {
        String json = String.format("{\"budget\":%d,\"situation\":\"%s\"}", budget, situation);
        return new ObjectMapper().readValue(json, ShoppingRecommendRequest.class);
    }

    private AiShoppingRecommendResponse shoppingResponse() throws Exception {
        String json = """
                {
                  "items": [
                    {"item": "네이비 셔츠", "reason": "체형에 잘 맞음", "estimatedPrice": 45000, "site": "무신사", "searchKeyword": "네이비 셔츠"}
                  ],
                  "totalEstimatedPrice": 140000,
                  "usageTip": "총 3가지 코디가 가능해요"
                }
                """;
        return new ObjectMapper().readValue(json, AiShoppingRecommendResponse.class);
    }

    @Test
    @DisplayName("존재하는 사용자의 체형/취향 프로필과 예산/상황으로 AI 서버를 호출하고 그 결과를 그대로 반환한다")
    void recommend_returnsAiResult_whenUserExists() throws Exception {
        // given
        ShoppingRecommendRequest request = shoppingRequest(150000, "출근");
        AiShoppingRecommendResponse aiResponse = shoppingResponse();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(aiServerClient.recommendShopping(any()))
                .willReturn(CompletableFuture.completedFuture(aiResponse));

        // when
        AiShoppingRecommendResponse result = shoppingService.recommend(userId, request);

        // then
        assertThat(result.getUsageTip()).isEqualTo("총 3가지 코디가 가능해요");
        assertThat(result.getTotalEstimatedPrice()).isEqualTo(140000);
        assertThat(result.getItems()).hasSize(1);

        ArgumentCaptor<AiShoppingRecommendRequest> captor = ArgumentCaptor.forClass(AiShoppingRecommendRequest.class);
        verify(aiServerClient).recommendShopping(captor.capture());
        AiShoppingRecommendRequest sentRequest = captor.getValue();
        assertThat(sentRequest.getBudget()).isEqualTo(150000);
        assertThat(sentRequest.getSituation()).isEqualTo("출근");
        assertThat(sentRequest.getBodyProfile().getHeight()).isEqualTo(180);
        assertThat(sentRequest.getBodyProfile().getBodyType()).isEqualTo("NORMAL");
        assertThat(sentRequest.getBodyProfile().getPreferredStyle()).isEqualTo("STREET");
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 USER_NOT_FOUND 예외를 던지고 AI 서버는 호출하지 않는다")
    void recommend_throwsUserNotFound_whenUserDoesNotExist() throws Exception {
        // given
        ShoppingRecommendRequest request = shoppingRequest(150000, "출근");
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> shoppingService.recommend(userId, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
        verify(aiServerClient, never()).recommendShopping(any());
    }

    @Test
    @DisplayName("AI 서버 호출이 실패하면 CompletionException으로 감싸지 않고 원래의 CustomException을 그대로 던진다")
    void recommend_unwrapsCustomException_whenAiServerFails() throws Exception {
        // given
        ShoppingRecommendRequest request = shoppingRequest(150000, "출근");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(aiServerClient.recommendShopping(any()))
                .willReturn(CompletableFuture.failedFuture(new CustomException(ErrorCode.AI_SERVER_ERROR)));

        // when & then
        assertThatThrownBy(() -> shoppingService.recommend(userId, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_SERVER_ERROR);
    }
}
