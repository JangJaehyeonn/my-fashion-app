package com.fashionapp.domain.outfit;

import com.fashionapp.domain.user.User;
import com.fashionapp.domain.user.UserRepository;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.infra.AiServerClient;
import com.fashionapp.infra.AiSituationRecommendRequest;
import com.fashionapp.infra.AiSituationRecommendResponse;
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
 * OutfitService 단위 테스트 — AI 서버(비동기) 호출 위임과 예외 전파를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class OutfitServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AiServerClient aiServerClient;

    private OutfitService outfitService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        outfitService = new OutfitService(userRepository, aiServerClient);
        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .email("test@example.com")
                .nickname("테스터")
                .provider(User.AuthProvider.google)
                .providerId("google-1")
                .height(175)
                .weight(68)
                .bodyType(User.BodyType.SLIM)
                .preferredStyle(User.PreferredStyle.CASUAL)
                .build();
    }

    private SituationRecommendRequest situationRequest(double temperature, String condition, String situation) throws Exception {
        String json = String.format(
                "{\"temperature\":%s,\"condition\":\"%s\",\"situation\":\"%s\"}",
                temperature, condition, situation);
        return new ObjectMapper().readValue(json, SituationRecommendRequest.class);
    }

    private AiSituationRecommendResponse situationResponse() throws Exception {
        String json = """
                {
                  "outfits": [
                    {
                      "description": "화이트 셔츠 + 슬랙스",
                      "reason": "출근룩으로 무난함",
                      "styleTag": "미니멀",
                      "shoppingSuggestions": [
                        {"item": "화이트 셔츠", "site": "무신사", "searchKeyword": "남성 화이트 셔츠"}
                      ]
                    }
                  ]
                }
                """;
        return new ObjectMapper().readValue(json, AiSituationRecommendResponse.class);
    }

    @Test
    @DisplayName("존재하는 사용자의 체형/취향 프로필과 상황 정보로 AI 서버를 호출하고 그 결과를 그대로 반환한다")
    void recommendBySituation_returnsAiResult_whenUserExists() throws Exception {
        // given
        SituationRecommendRequest request = situationRequest(20.0, "맑음", "출근");
        AiSituationRecommendResponse aiResponse = situationResponse();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(aiServerClient.recommendOutfitsBySituation(any()))
                .willReturn(CompletableFuture.completedFuture(aiResponse));

        // when
        AiSituationRecommendResponse result = outfitService.recommendBySituation(userId, request);

        // then
        assertThat(result.getOutfits()).hasSize(1);
        assertThat(result.getOutfits().get(0).getStyleTag()).isEqualTo("미니멀");

        ArgumentCaptor<AiSituationRecommendRequest> captor = ArgumentCaptor.forClass(AiSituationRecommendRequest.class);
        verify(aiServerClient).recommendOutfitsBySituation(captor.capture());
        AiSituationRecommendRequest sentRequest = captor.getValue();
        assertThat(sentRequest.getSituation()).isEqualTo("출근");
        assertThat(sentRequest.getWeather().getTemperature()).isEqualTo(20.0);
        assertThat(sentRequest.getWeather().getCondition()).isEqualTo("맑음");
        assertThat(sentRequest.getBodyProfile().getHeight()).isEqualTo(175);
        assertThat(sentRequest.getBodyProfile().getBodyType()).isEqualTo("SLIM");
        assertThat(sentRequest.getBodyProfile().getPreferredStyle()).isEqualTo("CASUAL");
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 USER_NOT_FOUND 예외를 던지고 AI 서버는 호출하지 않는다")
    void recommendBySituation_throwsUserNotFound_whenUserDoesNotExist() throws Exception {
        // given
        SituationRecommendRequest request = situationRequest(20.0, "맑음", "출근");
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> outfitService.recommendBySituation(userId, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
        verify(aiServerClient, never()).recommendOutfitsBySituation(any());
    }

    @Test
    @DisplayName("AI 서버 호출이 실패하면 CompletionException으로 감싸지 않고 원래의 CustomException을 그대로 던진다")
    void recommendBySituation_unwrapsCustomException_whenAiServerFails() throws Exception {
        // given
        SituationRecommendRequest request = situationRequest(20.0, "맑음", "출근");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(aiServerClient.recommendOutfitsBySituation(any()))
                .willReturn(CompletableFuture.failedFuture(new CustomException(ErrorCode.AI_SERVER_ERROR)));

        // when & then
        assertThatThrownBy(() -> outfitService.recommendBySituation(userId, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_SERVER_ERROR);
    }
}
