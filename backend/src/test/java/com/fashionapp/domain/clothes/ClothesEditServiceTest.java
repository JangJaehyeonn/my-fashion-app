package com.fashionapp.domain.clothes;

import com.fashionapp.domain.user.User;
import com.fashionapp.domain.user.UserRepository;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.infra.AiClothesClassifyResponse;
import com.fashionapp.infra.AiServerClient;
import com.fashionapp.infra.ImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** FR-01: user edit (PATCH) and reclassify of clothes. */
@ExtendWith(MockitoExtension.class)
class ClothesEditServiceTest {

    private static final String FILE = "22222222-2222-2222-2222-222222222222.jpg";
    private static final String KEY = "clothes/11111111-1111-1111-1111-111111111111/" + FILE;

    @Mock private ClothesRepository clothesRepository;
    @Mock private UserRepository userRepository;
    @Mock private AiServerClient aiServerClient;
    @Mock private ImageStorage imageStorage;
    @Mock private ClothesEmbedder clothesEmbedder;

    private ClothesService clothesService;
    private UUID userId;
    private UUID clothesId;
    private User user;

    @BeforeEach
    void setUp() {
        clothesService = new ClothesService(clothesRepository, userRepository, new ClothesClassifier(aiServerClient), imageStorage, clothesEmbedder);
        userId = UUID.randomUUID();
        clothesId = UUID.randomUUID();
        user = User.builder().ageConfirmedAt(LocalDateTime.now()).id(userId).nickname("테스터")
                .provider(User.AuthProvider.google).providerId("google-1").build();
    }

    private Clothes stored(String category, String color, String name) {
        Clothes clothes = Clothes.builder().id(clothesId).user(user).imageUrl(KEY)
                .category(category).color(color).name(name).build();
        given(clothesRepository.findByIdAndUser_Id(clothesId, userId)).willReturn(Optional.of(clothes));
        return clothes;
    }

    private static AiClothesClassifyResponse ai(String category, String color, String name) {
        AiClothesClassifyResponse response = new AiClothesClassifyResponse();
        ReflectionTestUtils.setField(response, "category", category);
        ReflectionTestUtils.setField(response, "color", color);
        ReflectionTestUtils.setField(response, "name", name);
        return response;
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(expected);
    }

    // ── 직접 수정 ──

    @Test
    @DisplayName("수정: 미분류 옷의 카테고리·색상·이름을 고치면 반영되고 분류 실패 표시가 풀린다")
    void update_unclassified_appliesValues() {
        Clothes clothes = stored("UNCLASSIFIED", null, null);

        ClothesResponse response = clothesService.update(userId, clothesId,
                new ClothesUpdateRequest("bottom", " 블랙 ", "블랙 슬랙스"));

        assertThat(clothes.getCategory()).isEqualTo("BOTTOM");
        assertThat(clothes.getColor()).isEqualTo("블랙");
        assertThat(clothes.getName()).isEqualTo("블랙 슬랙스");
        assertThat(response.getCategory()).isEqualTo(ClothesCategory.BOTTOM);
        assertThat(response.isClassificationFailed()).isFalse();
    }

    @Test
    @DisplayName("수정: 빠진 필드는 기존 값을 유지한다")
    void update_partial_keepsOtherFields() {
        Clothes clothes = stored("TOP", "네이비", "네이비 니트");

        clothesService.update(userId, clothesId, new ClothesUpdateRequest(null, null, "새 이름"));

        assertThat(clothes.getCategory()).isEqualTo("TOP");
        assertThat(clothes.getColor()).isEqualTo("네이비");
        assertThat(clothes.getName()).isEqualTo("새 이름");
    }

    @Test
    @DisplayName("수정: 미분류·모르는 카테고리, 빈 값, 너무 긴 값은 INVALID_CLOTHES_UPDATE(400)")
    void update_invalidValues_rejected() {
        List<ClothesUpdateRequest> invalid = List.of(
                new ClothesUpdateRequest("UNCLASSIFIED", null, null),
                new ClothesUpdateRequest("hat", null, null),
                new ClothesUpdateRequest(null, "  ", null),
                new ClothesUpdateRequest(null, null, "가".repeat(ClothesService.MAX_TEXT_LENGTH + 1)));

        for (ClothesUpdateRequest request : invalid) {
            assertError(() -> clothesService.update(userId, clothesId, request), ErrorCode.INVALID_CLOTHES_UPDATE);
        }
        assertThat(ErrorCode.INVALID_CLOTHES_UPDATE.getStatus().value()).isEqualTo(400);
        verify(clothesRepository, never()).findByIdAndUser_Id(any(), any());
    }

    @Test
    @DisplayName("수정: 내 옷이 아니면 CLOTHES_NOT_FOUND")
    void update_notOwned_throwsNotFound() {
        given(clothesRepository.findByIdAndUser_Id(clothesId, userId)).willReturn(Optional.empty());

        assertError(() -> clothesService.update(userId, clothesId, new ClothesUpdateRequest("TOP", null, null)),
                ErrorCode.CLOTHES_NOT_FOUND);
    }

    // ── 재분류 ──

    @Test
    @DisplayName("재분류: AI가 살아 있으면 저장된 사진을 보내 분류 결과로 갱신한다")
    void reclassify_success_updatesValues() {
        Clothes clothes = stored("UNCLASSIFIED", null, null);
        byte[] bytes = {9, 9, 9};
        given(imageStorage.readBytes(KEY)).willReturn(Optional.of(bytes));
        given(aiServerClient.classifyClothes(bytes, FILE, "image/jpeg")).willReturn(ai("SHOES", "화이트", "화이트 스니커즈"));
        given(clothesRepository.save(clothes)).willReturn(clothes);

        ClothesResponse response = clothesService.reclassify(userId, clothesId);

        assertThat(response.getCategory()).isEqualTo(ClothesCategory.SHOES);
        assertThat(response.getColor()).isEqualTo("화이트");
        assertThat(response.getName()).isEqualTo("화이트 스니커즈");
        assertThat(response.isClassificationFailed()).isFalse();
    }

    @Test
    @DisplayName("재분류: AI가 다운이면 503과 안내 문구, 사용자가 고친 기존 값은 그대로")
    void reclassify_aiDown_throws503AndKeepsValues() {
        Clothes clothes = stored("TOP", "네이비", "네이비 니트");
        given(imageStorage.readBytes(KEY)).willReturn(Optional.of(new byte[]{1}));
        willThrow(new CustomException(ErrorCode.AI_SERVER_ERROR))
                .given(aiServerClient).classifyClothes(any(byte[].class), anyString(), anyString());

        assertError(() -> clothesService.reclassify(userId, clothesId), ErrorCode.CLOTHES_RECLASSIFY_UNAVAILABLE);

        assertThat(ErrorCode.CLOTHES_RECLASSIFY_UNAVAILABLE.getStatus().value()).isEqualTo(503);
        assertThat(ErrorCode.CLOTHES_RECLASSIFY_UNAVAILABLE.getMessage()).isNotBlank();
        assertThat(clothes.getCategory()).isEqualTo("TOP");
        assertThat(clothes.getColor()).isEqualTo("네이비");
        assertThat(clothes.getName()).isEqualTo("네이비 니트");
        verify(clothesRepository, never()).save(any());
    }

    @Test
    @DisplayName("재분류: AI 타임아웃·잘못된 형식도 503이고 기존 값은 그대로")
    void reclassify_timeoutOrMalformed_throws503() {
        Clothes clothes = stored("UNCLASSIFIED", null, null);
        given(imageStorage.readBytes(KEY)).willReturn(Optional.of(new byte[]{1}));
        given(aiServerClient.classifyClothes(any(byte[].class), anyString(), anyString()))
                .willThrow(new CustomException(ErrorCode.AI_SERVER_TIMEOUT))
                .willReturn(ai("UNCLASSIFIED", "블랙", "x"))
                .willReturn(ai(null, null, null));

        for (int i = 0; i < 3; i++) {
            assertError(() -> clothesService.reclassify(userId, clothesId), ErrorCode.CLOTHES_RECLASSIFY_UNAVAILABLE);
        }
        assertThat(clothes.getCategory()).isEqualTo("UNCLASSIFIED");
        verify(clothesRepository, never()).save(any());
    }

    @Test
    @DisplayName("재분류: 저장된 사진을 읽지 못하면 CLOTHES_IMAGE_NOT_FOUND이고 AI는 호출하지 않는다")
    void reclassify_imageMissing_throws() {
        stored("UNCLASSIFIED", null, null);
        given(imageStorage.readBytes(KEY)).willReturn(Optional.empty());

        assertError(() -> clothesService.reclassify(userId, clothesId), ErrorCode.CLOTHES_IMAGE_NOT_FOUND);
        verify(aiServerClient, never()).classifyClothes(any(byte[].class), anyString(), anyString());
    }

    @Test
    @DisplayName("재분류: 내 옷이 아니면 CLOTHES_NOT_FOUND")
    void reclassify_notOwned_throwsNotFound() {
        given(clothesRepository.findByIdAndUser_Id(clothesId, userId)).willReturn(Optional.empty());

        assertError(() -> clothesService.reclassify(userId, clothesId), ErrorCode.CLOTHES_NOT_FOUND);
    }
}
