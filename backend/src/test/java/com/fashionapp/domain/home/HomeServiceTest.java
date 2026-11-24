package com.fashionapp.domain.home;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * HomeService 단위 테스트 — 쇼핑 도우미 제거(FR-23) 후 홈 요약은 상황 코디 추천만 AI 서버에 요청한다.
 */
@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AiServerClient aiServerClient;

    private HomeService homeService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        homeService = new HomeService(userRepository, aiServerClient);
        userId = UUID.randomUUID();
    }

    private HomeSummaryRequest summaryRequest() throws Exception {
        String json = "{\"temperature\":20.0,\"condition\":\"맑음\",\"situation\":\"출근\"}";
        return new ObjectMapper().readValue(json, HomeSummaryRequest.class);
    }

    private User user() {
        return User.builder()
                .id(userId)
                .provider(User.AuthProvider.google)
                .providerId("google-1")
                .height(175)
                .bodyType(User.BodyType.SLIM)
                .build();
    }

    @Test
    @DisplayName("홈 요약은 상황 코디 추천만 AI 서버에 요청하고 그 결과를 담는다")
    void getSummary_callsOnlySituationRecommend() throws Exception {
        AiSituationRecommendResponse aiResponse = new ObjectMapper()
                .readValue("{\"outfits\":[{\"description\":\"셔츠 + 슬랙스\"}]}", AiSituationRecommendResponse.class);
        given(userRepository.findById(userId)).willReturn(Optional.of(user()));
        given(aiServerClient.recommendOutfitsBySituation(any()))
                .willReturn(CompletableFuture.completedFuture(aiResponse));

        HomeSummaryResponse result = homeService.getSummary(userId, summaryRequest());

        assertThat(result.getOutfitRecommendation()).isSameAs(aiResponse);
        ArgumentCaptor<AiSituationRecommendRequest> captor = ArgumentCaptor.forClass(AiSituationRecommendRequest.class);
        verify(aiServerClient).recommendOutfitsBySituation(captor.capture());
        assertThat(captor.getValue().getSituation()).isEqualTo("출근");
        verifyNoMoreInteractions(aiServerClient);
    }

    @Test
    @DisplayName("AI 서버의 CustomException은 감싸지 않고 그대로 던진다")
    void getSummary_rethrowsCustomException() throws Exception {
        given(userRepository.findById(userId)).willReturn(Optional.of(user()));
        given(aiServerClient.recommendOutfitsBySituation(any()))
                .willReturn(CompletableFuture.failedFuture(new CustomException(ErrorCode.AI_SERVER_ERROR)));

        assertThatThrownBy(() -> homeService.getSummary(userId, summaryRequest()))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("사용자가 없으면 AI 서버를 부르지 않고 USER_NOT_FOUND")
    void getSummary_throwsWhenUserMissing() throws Exception {
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> homeService.getSummary(userId, summaryRequest()))
                .isInstanceOf(CustomException.class);
        verifyNoMoreInteractions(aiServerClient);
    }
}
