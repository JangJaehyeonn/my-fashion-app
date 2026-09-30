package com.fashionapp.domain.clothes;

import com.fashionapp.domain.user.User;
import com.fashionapp.domain.user.UserRepository;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.infra.AiClothesClassifyResponse;
import com.fashionapp.infra.AiServerClient;
import com.fashionapp.infra.S3Uploader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * ClothesService 단위 테스트 — AI 분류 → S3 업로드 → 저장 순서와 카테고리 정규화, 삭제 동작을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ClothesServiceTest {

    @Mock
    private ClothesRepository clothesRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private S3Uploader s3Uploader;

    @Mock
    private AiServerClient aiServerClient;

    private ClothesService clothesService;

    private UUID userId;
    private User user;
    private MultipartFile image;

    @BeforeEach
    void setUp() {
        clothesService = new ClothesService(clothesRepository, userRepository, s3Uploader, aiServerClient);
        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .nickname("테스터")
                .provider(User.AuthProvider.google)
                .providerId("google-1")
                .build();
        image = new MockMultipartFile("image", "shirt.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    private AiClothesClassifyResponse aiResult(String category, String color, String name) {
        AiClothesClassifyResponse result = mock(AiClothesClassifyResponse.class);
        given(result.getCategory()).willReturn(category);
        given(result.getColor()).willReturn(color);
        given(result.getName()).willReturn(name);
        return result;
    }

    @Test
    @DisplayName("옷 등록: AI 분류 후 S3 업로드하고 분류 결과를 저장한다")
    void register_classifiesThenUploadsAndSaves() {
        AiClothesClassifyResponse ai = aiResult("TOP", "그레이", "그레이 니트 스웨터");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(aiServerClient.classifyClothes(image)).willReturn(ai);
        given(s3Uploader.upload(image, "clothes")).willReturn("https://bucket.s3.region.amazonaws.com/clothes/a.jpg");
        given(clothesRepository.save(any(Clothes.class))).willAnswer(inv -> inv.getArgument(0));
        given(s3Uploader.generatePresignedUrl(anyString())).willReturn("https://presigned");

        ClothesResponse response = clothesService.register(userId, image);

        InOrder order = inOrder(aiServerClient, s3Uploader);
        order.verify(aiServerClient).classifyClothes(image);
        order.verify(s3Uploader).upload(image, "clothes");

        ArgumentCaptor<Clothes> captor = ArgumentCaptor.forClass(Clothes.class);
        verify(clothesRepository).save(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo("TOP");
        assertThat(captor.getValue().getName()).isEqualTo("그레이 니트 스웨터");

        assertThat(response.getCategory()).isEqualTo(ClothesCategory.TOP);
        assertThat(response.getColor()).isEqualTo("그레이");
        assertThat(response.getImageUrl()).isEqualTo("https://presigned");
    }

    @Test
    @DisplayName("옷 등록: AI가 목록 밖 카테고리를 주면 ETC로 저장한다")
    void register_unknownCategory_fallsBackToEtc() {
        AiClothesClassifyResponse ai = aiResult("hat", "블랙", "블랙 볼캡");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(aiServerClient.classifyClothes(image)).willReturn(ai);
        given(s3Uploader.upload(image, "clothes")).willReturn("https://bucket.s3.region.amazonaws.com/clothes/b.jpg");
        given(clothesRepository.save(any(Clothes.class))).willAnswer(inv -> inv.getArgument(0));

        ClothesResponse response = clothesService.register(userId, image);

        assertThat(response.getCategory()).isEqualTo(ClothesCategory.ETC);
    }

    @Test
    @DisplayName("옷 등록: AI 분류가 실패하면 S3에 업로드하지 않는다")
    void register_aiFailure_doesNotUpload() {
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        willThrow(new CustomException(ErrorCode.AI_SERVER_ERROR)).given(aiServerClient).classifyClothes(image);

        assertThatThrownBy(() -> clothesService.register(userId, image))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_SERVER_ERROR);

        verify(s3Uploader, never()).upload(any(), anyString());
        verify(clothesRepository, never()).save(any());
    }

    @Test
    @DisplayName("옷 목록: 옛 스키마의 자유 텍스트 카테고리도 ETC로 조회된다")
    void getMyClothes_legacyCategory_mapsToEtc() {
        Clothes legacy = Clothes.builder()
                .id(UUID.randomUUID())
                .user(user)
                .imageUrl("https://bucket.s3.region.amazonaws.com/clothes/old.jpg")
                .category("상의")
                .build();
        given(clothesRepository.findByUser_IdOrderByCreatedAtDesc(userId)).willReturn(List.of(legacy));

        List<ClothesResponse> result = clothesService.getMyClothes(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategory()).isEqualTo(ClothesCategory.ETC);
    }

    @Test
    @DisplayName("옷 삭제: 내 옷이 아니면 CLOTHES_NOT_FOUND")
    void delete_notOwned_throwsNotFound() {
        UUID clothesId = UUID.randomUUID();
        given(clothesRepository.findByIdAndUser_Id(clothesId, userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> clothesService.delete(userId, clothesId))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.CLOTHES_NOT_FOUND);

        verify(clothesRepository, never()).delete(any());
    }

    @Test
    @DisplayName("옷 삭제: S3 삭제가 실패해도 DB 삭제는 유지되고 예외를 던지지 않는다")
    void delete_s3Failure_stillDeletesRow() {
        UUID clothesId = UUID.randomUUID();
        Clothes clothes = Clothes.builder()
                .id(clothesId)
                .user(user)
                .imageUrl("https://bucket.s3.region.amazonaws.com/clothes/c.jpg")
                .category("TOP")
                .build();
        given(clothesRepository.findByIdAndUser_Id(clothesId, userId)).willReturn(Optional.of(clothes));
        willThrow(new RuntimeException("AccessDenied")).given(s3Uploader).delete(clothes.getImageUrl());

        clothesService.delete(userId, clothesId);

        verify(clothesRepository).delete(clothes);
    }
}
