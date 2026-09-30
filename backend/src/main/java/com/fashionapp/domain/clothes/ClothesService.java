package com.fashionapp.domain.clothes;

import com.fashionapp.domain.user.User;
import com.fashionapp.domain.user.UserRepository;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.infra.AiClothesClassifyResponse;
import com.fashionapp.infra.AiServerClient;
import com.fashionapp.infra.S3Uploader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClothesService {

    private final ClothesRepository clothesRepository;
    private final UserRepository userRepository;
    private final S3Uploader s3Uploader;
    private final AiServerClient aiServerClient;

    @Transactional
    public ClothesResponse register(UUID userId, MultipartFile image) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // AI 분류를 먼저 해서, 분류 실패 시 S3에 고아 파일이 남지 않도록 함
        AiClothesClassifyResponse aiResult = aiServerClient.classifyClothes(image);
        String imageUrl = s3Uploader.upload(image, "clothes");

        Clothes clothes = Clothes.builder()
                .user(user)
                .imageUrl(imageUrl)
                .category(ClothesCategory.from(aiResult.getCategory()).name())
                .color(aiResult.getColor())
                .name(aiResult.getName())
                .build();

        return toResponse(clothesRepository.save(clothes));
    }

    @Transactional(readOnly = true)
    public List<ClothesResponse> getMyClothes(UUID userId) {
        return clothesRepository.findByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void delete(UUID userId, UUID clothesId) {
        Clothes clothes = clothesRepository.findByIdAndUser_Id(clothesId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CLOTHES_NOT_FOUND));

        clothesRepository.delete(clothes);
        try {
            s3Uploader.delete(clothes.getImageUrl());
        } catch (RuntimeException e) {
            // DB 행은 이미 지웠으므로 S3 정리 실패가 사용자 요청을 실패시키지 않게 로그만 남김
            log.warn("S3 image delete failed for clothes {}: {}", clothesId, e.getMessage());
        }
    }

    private ClothesResponse toResponse(Clothes clothes) {
        return ClothesResponse.builder()
                .id(clothes.getId())
                .imageUrl(s3Uploader.generatePresignedUrl(clothes.getImageUrl()))
                .category(ClothesCategory.from(clothes.getCategory()))
                .color(clothes.getColor())
                .name(clothes.getName())
                .createdAt(clothes.getCreatedAt())
                .build();
    }
}
