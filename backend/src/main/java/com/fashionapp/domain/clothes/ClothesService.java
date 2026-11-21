package com.fashionapp.domain.clothes;

import com.fashionapp.domain.user.User;
import com.fashionapp.domain.user.UserRepository;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.infra.AiClothesClassifyResponse;
import com.fashionapp.infra.AiServerClient;
import com.fashionapp.infra.ImageStorage;
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
    private final AiServerClient aiServerClient;
    private final ImageStorage imageStorage;

    // AI 서버 호출(수 초~수 분)을 기다리는 동안 DB 커넥션을 붙잡지 않도록 트랜잭션을 걸지 않음 —
    // 조회·저장은 각 리포지토리 호출이 짧은 트랜잭션으로 처리 (open-in-view도 꺼져 있어야 함, application.yml)
    public ClothesResponse register(UUID userId, MultipartFile image) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // AI 분류를 먼저 해서, 분류 실패 시 저장소에 고아 파일이 남지 않도록 함
        long t0 = System.nanoTime();
        AiClothesClassifyResponse aiResult = aiServerClient.classifyClothes(image);
        long t1 = System.nanoTime();
        String imageUrl = imageStorage.store(image, "clothes", userId);
        long t2 = System.nanoTime();

        Clothes clothes = Clothes.builder()
                .user(user)
                .imageUrl(imageUrl)
                .category(ClothesCategory.from(aiResult.getCategory()).name())
                .color(aiResult.getColor())
                .name(aiResult.getName())
                .build();

        ClothesResponse response = toResponse(clothesRepository.save(clothes));
        long t3 = System.nanoTime();
        // 옷 등록이 느릴 때 어느 구간(AI 분류 / 이미지 저장 / DB·URL 변환)인지 가려내기 위한 구간별 소요 시간
        log.info("clothes.register size={}KB classify={}ms s3={}ms db={}ms",
                image.getSize() / 1024, (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, (t3 - t2) / 1_000_000);
        return response;
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
            imageStorage.delete(clothes.getImageUrl());
        } catch (RuntimeException e) {
            // DB 행은 이미 지웠으므로 저장소 정리 실패가 사용자 요청을 실패시키지 않게 로그만 남김
            log.warn("image delete failed for clothes {}: {}", clothesId, e.getMessage());
        }
    }

    private ClothesResponse toResponse(Clothes clothes) {
        return ClothesResponse.builder()
                .id(clothes.getId())
                .imageUrl(imageStorage.resolveUrl(clothes.getImageUrl()))
                .category(ClothesCategory.from(clothes.getCategory()))
                .color(clothes.getColor())
                .name(clothes.getName())
                .createdAt(clothes.getCreatedAt())
                .build();
    }
}
