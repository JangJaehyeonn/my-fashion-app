package com.fashionapp.domain.outfit;

import com.fashionapp.domain.clothes.ClothesCategory;
import com.fashionapp.domain.clothes.ClothesResponse;
import com.fashionapp.domain.clothes.ClothesService;
import com.fashionapp.domain.user.User;
import com.fashionapp.domain.user.UserRepository;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.infra.AiClosetRecommendRequest;
import com.fashionapp.infra.AiClosetRecommendResponse;
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

import java.util.List;
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

    @Mock
    private ClothesService clothesService;

    private OutfitService outfitService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        outfitService = new OutfitService(userRepository, aiServerClient, clothesService);
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

    private ClothesResponse clothes(String id, ClothesCategory category, String name) {
        return ClothesResponse.builder()
                .id(UUID.fromString(id))
                .imageUrl("https://presigned/" + id)
                .category(category)
                .color("블랙")
                .name(name)
                .build();
    }

    private AiClosetRecommendResponse closetResponse(String json) throws Exception {
        return new ObjectMapper().readValue(json, AiClosetRecommendResponse.class);
    }

    private static final String TOP_ID = "00000000-0000-0000-0000-000000000001";
    private static final String BOTTOM_ID = "00000000-0000-0000-0000-000000000002";
    private static final String SHOES_ID = "00000000-0000-0000-0000-000000000003";

    @Test
    @DisplayName("옷장 추천: 내 옷 목록을 AI에 넘기고, 응답 ID를 실제 옷 정보(사진 URL 포함)로 바꿔 반환한다")
    void recommendByCloset_mapsIdsToClothes() throws Exception {
        SituationRecommendRequest request = situationRequest(14.0, "맑음", "DATE");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(clothesService.getMyClothes(userId)).willReturn(List.of(
                clothes(TOP_ID, ClothesCategory.TOP, "블랙 티셔츠"),
                clothes(BOTTOM_ID, ClothesCategory.BOTTOM, "블랙 슬랙스"),
                clothes(SHOES_ID, ClothesCategory.SHOES, "블랙 로퍼")));
        given(aiServerClient.recommendOutfitsByCloset(any())).willReturn(CompletableFuture.completedFuture(closetResponse("""
                {"outfits": [{"clothesIds": ["%s", "%s", "%s"], "reason": "올블랙", "styleTag": "미니멀"}]}
                """.formatted(TOP_ID, BOTTOM_ID, SHOES_ID))));

        ClosetRecommendResponse result = outfitService.recommendByCloset(userId, request);

        assertThat(result.getOutfits()).hasSize(1);
        assertThat(result.getOutfits().get(0).getItems())
                .extracting(ClothesResponse::getName)
                .containsExactly("블랙 티셔츠", "블랙 슬랙스", "블랙 로퍼");
        assertThat(result.getOutfits().get(0).getItems().get(0).getImageUrl()).isEqualTo("https://presigned/" + TOP_ID);

        ArgumentCaptor<AiClosetRecommendRequest> captor = ArgumentCaptor.forClass(AiClosetRecommendRequest.class);
        verify(aiServerClient).recommendOutfitsByCloset(captor.capture());
        assertThat(captor.getValue().getClothes())
                .extracting(AiClosetRecommendRequest.ClosetItem::getCategory)
                .containsExactly("TOP", "BOTTOM", "SHOES");
        assertThat(captor.getValue().getSituation()).isEqualTo("DATE");
    }

    @Test
    @DisplayName("옷장 추천: 내 옷장에 없는 ID는 버리고, 남은 옷이 2개 미만인 조합은 제외한다")
    void recommendByCloset_dropsUnknownIdsAndTooSmallOutfits() throws Exception {
        SituationRecommendRequest request = situationRequest(20.0, "맑음", "DAILY");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(clothesService.getMyClothes(userId)).willReturn(List.of(
                clothes(TOP_ID, ClothesCategory.TOP, "블랙 티셔츠"),
                clothes(BOTTOM_ID, ClothesCategory.BOTTOM, "블랙 슬랙스")));
        given(aiServerClient.recommendOutfitsByCloset(any())).willReturn(CompletableFuture.completedFuture(closetResponse("""
                {"outfits": [
                  {"clothesIds": ["%s", "%s", "99999999-9999-9999-9999-999999999999"], "reason": "ok", "styleTag": "캐주얼"},
                  {"clothesIds": ["%s", "99999999-9999-9999-9999-999999999999"], "reason": "too small", "styleTag": "캐주얼"}
                ]}
                """.formatted(TOP_ID, BOTTOM_ID, TOP_ID))));

        ClosetRecommendResponse result = outfitService.recommendByCloset(userId, request);

        assertThat(result.getOutfits()).hasSize(1);
        assertThat(result.getOutfits().get(0).getItems()).hasSize(2);
        assertThat(result.getOutfits().get(0).getReason()).isEqualTo("ok");
    }

    @Test
    @DisplayName("옷장 추천: 옷장이 비어 있으면 CLOSET_EMPTY를 던지고 AI 서버는 호출하지 않는다")
    void recommendByCloset_throwsClosetEmpty() throws Exception {
        SituationRecommendRequest request = situationRequest(20.0, "맑음", "DAILY");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(clothesService.getMyClothes(userId)).willReturn(List.of());

        assertThatThrownBy(() -> outfitService.recommendByCloset(userId, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.CLOSET_EMPTY);
        verify(aiServerClient, never()).recommendOutfitsByCloset(any());
    }
}
