package com.fashionapp.domain.clothes;

import com.fashionapp.domain.user.User;
import com.fashionapp.domain.user.UserRepository;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.infra.AiClassifyResponse;
import com.fashionapp.infra.AiServerClient;
import com.fashionapp.infra.S3Uploader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClothesService {

    private final ClothesRepository clothesRepository;
    private final UserRepository userRepository;
    private final S3Uploader s3Uploader;
    private final AiServerClient aiServerClient;

    @Transactional(readOnly = true)
    public List<ClothesResponse> getMyClothes(UUID userId) {
        return clothesRepository.findByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(ClothesResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public ClothesResponse upload(UUID userId, MultipartFile image) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String imageUrl = s3Uploader.upload(image, "clothes");

        AiClassifyResponse aiResult = aiServerClient.classifyClothes(image);

        Clothes clothes = Clothes.builder()
                .user(user)
                .imageUrl(imageUrl)
                .category(aiResult.getCategory())
                .color(aiResult.getColor())
                .pattern(aiResult.getPattern())
                .season(aiResult.getSeason())
                .styleTag(aiResult.getStyleTag())
                .build();

        return ClothesResponse.from(clothesRepository.save(clothes));
    }

    @Transactional(readOnly = true)
    public ClothesResponse getClothes(UUID userId, UUID clothesId) {
        Clothes clothes = clothesRepository.findByIdAndUser_Id(clothesId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CLOTHES_NOT_FOUND));
        return ClothesResponse.from(clothes);
    }

    @Transactional
    public ClothesResponse update(UUID userId, UUID clothesId, ClothesUpdateRequest request) {
        Clothes clothes = clothesRepository.findByIdAndUser_Id(clothesId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CLOTHES_NOT_FOUND));
        clothes.update(request);
        return ClothesResponse.from(clothes);
    }

    @Transactional
    public void delete(UUID userId, UUID clothesId) {
        Clothes clothes = clothesRepository.findByIdAndUser_Id(clothesId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CLOTHES_NOT_FOUND));
        s3Uploader.delete(clothes.getImageUrl());
        clothesRepository.delete(clothes);
    }
}
