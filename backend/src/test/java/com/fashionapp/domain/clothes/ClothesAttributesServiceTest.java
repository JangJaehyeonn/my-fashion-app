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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** FR-07: fit·material·pattern·season on register, PATCH and reclassify, and embedding refresh. */
@ExtendWith(MockitoExtension.class)
class ClothesAttributesServiceTest {

    private static final String KEY = "clothes/11111111-1111-1111-1111-111111111111/22222222-2222-2222-2222-222222222222.jpg";

    @Mock private ClothesRepository clothesRepository;
    @Mock private UserRepository userRepository;
    @Mock private AiServerClient aiServerClient;
    @Mock private ImageStorage imageStorage;
    @Mock private ClothesEmbedder clothesEmbedder;
    @org.mockito.Mock private com.fashionapp.domain.wear.WearRecordRepository wearRecordRepository;

    private ClothesService clothesService;
    private UUID userId;
    private UUID clothesId;
    private User user;

    @BeforeEach
    void setUp() {
        clothesService = new ClothesService(clothesRepository, userRepository, new ClothesClassifier(aiServerClient),
                imageStorage, clothesEmbedder, com.fashionapp.domain.combo.ComboTestSupport.defaultService(), wearRecordRepository,
                org.mockito.Mockito.mock(com.fashionapp.domain.event.EventRecorder.class),
                ClothesAvailabilityTest.defaultAvailability());
        userId = UUID.randomUUID();
        clothesId = UUID.randomUUID();
        user = User.builder().ageConfirmedAt(LocalDateTime.now()).id(userId).nickname("테스터")
                .provider(User.AuthProvider.google).providerId("google-1").build();
    }

    private static AiClothesClassifyResponse ai(String fit, String material, String pattern, String season) {
        AiClothesClassifyResponse response = new AiClothesClassifyResponse();
        ReflectionTestUtils.setField(response, "category", "TOP");
        ReflectionTestUtils.setField(response, "color", "네이비");
        ReflectionTestUtils.setField(response, "name", "네이비 니트");
        ReflectionTestUtils.setField(response, "fit", fit);
        ReflectionTestUtils.setField(response, "material", material);
        ReflectionTestUtils.setField(response, "pattern", pattern);
        ReflectionTestUtils.setField(response, "season", season);
        return response;
    }

    private Clothes stored() {
        Clothes clothes = Clothes.builder().id(clothesId).user(user).imageUrl(KEY)
                .category("TOP").color("네이비").name("니트").material("울").build();
        given(clothesRepository.findByIdAndUser_Id(clothesId, userId)).willReturn(Optional.of(clothes));
        return clothes;
    }

    @Test
    @DisplayName("등록하면 AI 속성이 응답에 오고 임베딩을 계산한다")
    void register_returnsAttributesAndEmbeds() {
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(aiServerClient.classifyClothes(any(org.springframework.web.multipart.MultipartFile.class)))
                .willReturn(ai("슬림", "울", "스트라이프", "winter"));
        given(imageStorage.store(any(), anyString(), any())).willReturn(KEY);
        given(clothesRepository.save(any(Clothes.class))).willAnswer(inv -> inv.getArgument(0));

        ClothesResponse response = clothesService.register(userId,
                new MockMultipartFile("image", "a.jpg", "image/jpeg", new byte[]{1}));

        assertThat(response.getFit()).isEqualTo("슬림");
        assertThat(response.getMaterial()).isEqualTo("울");
        assertThat(response.getPattern()).isEqualTo("스트라이프");
        assertThat(response.getSeason()).isEqualTo(ClothesSeason.WINTER);
        verify(clothesEmbedder).refresh(any(Clothes.class));
    }

    @Test
    @DisplayName("AI가 속성을 모르면(없음·빈 값·목록 밖 계절) null이다")
    void register_unknownAttributes_null() {
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(aiServerClient.classifyClothes(any(org.springframework.web.multipart.MultipartFile.class)))
                .willReturn(ai(null, " ", null, "RAINY"));
        given(imageStorage.store(any(), anyString(), any())).willReturn(KEY);
        given(clothesRepository.save(any(Clothes.class))).willAnswer(inv -> inv.getArgument(0));

        ClothesResponse response = clothesService.register(userId,
                new MockMultipartFile("image", "a.jpg", "image/jpeg", new byte[]{1}));

        assertThat(response.getFit()).isNull();
        assertThat(response.getMaterial()).isNull();
        assertThat(response.getPattern()).isNull();
        assertThat(response.getSeason()).isNull();
    }

    @Test
    @DisplayName("PATCH로 속성을 고치면 바뀐 값을 저장하고 임베딩을 다시 계산한다")
    void update_attributes_recomputesEmbedding() {
        Clothes clothes = stored();

        ClothesResponse response = clothesService.update(userId, clothesId,
                new ClothesUpdateRequest(null, null, null, "오버핏", "면", null, "fall"));

        assertThat(response.getFit()).isEqualTo("오버핏");
        assertThat(response.getMaterial()).isEqualTo("면");
        assertThat(response.getPattern()).isNull();
        assertThat(response.getSeason()).isEqualTo(ClothesSeason.FALL);
        assertThat(response.getName()).isEqualTo("니트");
        verify(clothesRepository).save(clothes);
        verify(clothesEmbedder).refresh(clothes);
    }

    @Test
    @DisplayName("생략한 속성은 그대로 둔다")
    void update_omittedAttributes_kept() {
        stored();

        ClothesResponse response = clothesService.update(userId, clothesId, new ClothesUpdateRequest(null, null, "새 이름"));

        assertThat(response.getMaterial()).isEqualTo("울");
        assertThat(response.getName()).isEqualTo("새 이름");
    }

    @Test
    @DisplayName("잘못된 계절이나 너무 긴 속성은 400이고 임베딩을 건드리지 않는다")
    void update_invalidAttributes_rejected() {
        for (ClothesUpdateRequest request : new ClothesUpdateRequest[]{
                new ClothesUpdateRequest(null, null, null, null, null, null, "RAINY"),
                new ClothesUpdateRequest(null, null, null, "가".repeat(51), null, null, null),
                new ClothesUpdateRequest(null, null, null, null, " ", null, null)}) {
            assertThatThrownBy(() -> clothesService.update(userId, clothesId, request))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_CLOTHES_UPDATE);
        }
        verify(clothesEmbedder, never()).refresh(any());
    }

    @Test
    @DisplayName("재분류하면 새 속성으로 임베딩을 다시 계산한다")
    void reclassify_recomputesEmbedding() {
        Clothes clothes = stored();
        given(imageStorage.readBytes(KEY)).willReturn(Optional.of(new byte[]{1}));
        given(aiServerClient.classifyClothes(any(byte[].class), anyString(), anyString()))
                .willReturn(ai("레귤러", "면", "무지", "SUMMER"));
        given(clothesRepository.save(clothes)).willReturn(clothes);

        ClothesResponse response = clothesService.reclassify(userId, clothesId);

        assertThat(response.getMaterial()).isEqualTo("면");
        assertThat(response.getSeason()).isEqualTo(ClothesSeason.SUMMER);
        verify(clothesEmbedder).refresh(clothes);
    }
}
