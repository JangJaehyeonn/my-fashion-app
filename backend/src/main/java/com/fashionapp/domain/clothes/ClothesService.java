package com.fashionapp.domain.clothes;

import com.fashionapp.domain.combo.ComboCountService;
import com.fashionapp.domain.combo.ComboStats;
import com.fashionapp.domain.user.User;
import com.fashionapp.domain.user.UserRepository;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.infra.ImageKeys;
import com.fashionapp.infra.ImageStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClothesService {

    static final int MAX_TEXT_LENGTH = 50;

    private final ClothesRepository clothesRepository;
    private final UserRepository userRepository;
    private final ClothesClassifier clothesClassifier;
    private final ImageStorage imageStorage;
    private final ClothesEmbedder clothesEmbedder;
    private final ComboCountService comboCountService;

    // AI 서버 호출(수 초~수 분)을 기다리는 동안 DB 커넥션을 붙잡지 않도록 트랜잭션을 걸지 않음 —
    // 조회·저장은 각 리포지토리 호출이 짧은 트랜잭션으로 처리 (open-in-view도 꺼져 있어야 함, application.yml)
    public ClothesResponse register(UUID userId, MultipartFile image) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // FR-01: AI 분류가 실패(연결 실패·타임아웃·5xx·잘못된 응답)해도 사진은 저장하고 미분류로 등록한다
        long t0 = System.nanoTime();
        Optional<ClothesClassification> classification = clothesClassifier.classify(image);
        long t1 = System.nanoTime();
        String imageUrl = imageStorage.store(image, "clothes", userId);
        long t2 = System.nanoTime();

        Clothes clothes = Clothes.builder()
                .user(user)
                .imageUrl(imageUrl)
                .category(ClothesCategory.UNCLASSIFIED.name())
                .build();
        classification.ifPresent(clothes::applyClassification);

        Clothes saved = clothesRepository.save(clothes);
        long t3 = System.nanoTime();
        // FR-07: embedding failure must not fail registration (the embedding just stays empty)
        clothesEmbedder.refresh(saved);
        long t4 = System.nanoTime();
        ClothesResponse response = toResponse(saved);
        // 옷 등록이 느릴 때 어느 구간(AI 분류 / 이미지 저장 / DB·URL 변환)인지 가려내기 위한 구간별 소요 시간
        log.info("clothes.register size={}KB classified={} classify={}ms s3={}ms db={}ms embed={}ms",
                image.getSize() / 1024, classification.isPresent(),
                (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, (t3 - t2) / 1_000_000, (t4 - t3) / 1_000_000);
        return response;
    }

    @Transactional(readOnly = true)
    public List<ClothesResponse> getMyClothes(UUID userId) {
        List<Clothes> closet = clothesRepository.findByUser_IdOrderByCreatedAtDesc(userId);
        ComboStats stats = comboCountService.statsFor(userId, closet);
        return closet.stream()
                .map(clothes -> toResponse(clothes, stats.countOf(clothes.getId())))
                .toList();
    }

    /** FR-09: closet summary with the total number of valid combos. */
    @Transactional(readOnly = true)
    public ClosetSummaryResponse getSummary(UUID userId) {
        List<Clothes> closet = clothesRepository.findByUser_IdOrderByCreatedAtDesc(userId);
        return new ClosetSummaryResponse(comboCountService.statsFor(userId, closet).total());
    }

    // 임베딩 재계산(AI 호출)을 기다리는 동안 커넥션을 붙잡지 않도록 트랜잭션을 걸지 않음 (register와 같은 이유)
    public ClothesResponse update(UUID userId, UUID clothesId, ClothesUpdateRequest request) {
        ClothesCategory category = request.getCategory() == null ? null
                : ClothesCategory.parseAssignable(request.getCategory())
                        .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CLOTHES_UPDATE));
        String color = normalizeText(request.getColor());
        String name = normalizeText(request.getName());
        String fit = normalizeText(request.getFit());
        String material = normalizeText(request.getMaterial());
        String pattern = normalizeText(request.getPattern());
        ClothesSeason season = request.getSeason() == null ? null
                : ClothesSeason.parse(request.getSeason())
                        .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CLOTHES_UPDATE));

        Clothes clothes = findOwned(userId, clothesId);
        clothes.editByUser(category, color, name);
        clothes.editAttributesByUser(fit, material, pattern, season);
        clothesRepository.save(clothes);
        // FR-07: attributes changed, so the embedding is recomputed (failure only empties it)
        clothesEmbedder.refresh(clothes);
        return toResponse(clothes);
    }

    // AI 호출 중에는 트랜잭션을 걸지 않음 (register와 같은 이유). 실패하면 기존 값을 건드리지 않고 503
    public ClothesResponse reclassify(UUID userId, UUID clothesId) {
        String key = ImageKeys.toKey(findOwned(userId, clothesId).getImageUrl());
        byte[] bytes = imageStorage.readBytes(key)
                .orElseThrow(() -> new CustomException(ErrorCode.CLOTHES_IMAGE_NOT_FOUND));

        ClothesClassification classification = clothesClassifier
                .classify(bytes, fileNameOf(key), ImageKeys.contentTypeOf(key))
                .orElseThrow(() -> new CustomException(ErrorCode.CLOTHES_RECLASSIFY_UNAVAILABLE));

        // AI를 기다리는 동안 삭제됐을 수 있으므로 다시 읽어서 갱신
        Clothes clothes = findOwned(userId, clothesId);
        clothes.applyClassification(classification);
        Clothes saved = clothesRepository.save(clothes);
        clothesEmbedder.refresh(saved);
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID userId, UUID clothesId) {
        Clothes clothes = findOwned(userId, clothesId);

        clothesRepository.delete(clothes);
        try {
            imageStorage.delete(clothes.getImageUrl());
        } catch (RuntimeException e) {
            // DB 행은 이미 지웠으므로 저장소 정리 실패가 사용자 요청을 실패시키지 않게 로그만 남김
            log.warn("image delete failed for clothes {}: {}", clothesId, e.getMessage());
        }
    }

    private Clothes findOwned(UUID userId, UUID clothesId) {
        return clothesRepository.findByIdAndUser_Id(clothesId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CLOTHES_NOT_FOUND));
    }

    /** null stays null (field not changed). Otherwise trimmed and must be 1..MAX_TEXT_LENGTH chars. */
    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_TEXT_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_CLOTHES_UPDATE);
        }
        return trimmed;
    }

    private static String fileNameOf(String key) {
        return key.substring(key.lastIndexOf('/') + 1);
    }

    private ClothesResponse toResponse(Clothes clothes) {
        return toResponse(clothes, null);
    }

    private ClothesResponse toResponse(Clothes clothes, Integer comboCount) {
        ClothesCategory category = ClothesCategory.from(clothes.getCategory());
        return ClothesResponse.builder()
                .id(clothes.getId())
                .imageUrl(imageStorage.resolveUrl(clothes.getImageUrl()))
                .category(category)
                .color(clothes.getColor())
                .name(clothes.getName())
                .classificationFailed(category == ClothesCategory.UNCLASSIFIED)
                .createdAt(clothes.getCreatedAt())
                .fit(clothes.getFit())
                .material(clothes.getMaterial())
                .pattern(clothes.getPattern())
                .season(ClothesSeason.parse(clothes.getSeason()).orElse(null))
                .comboCount(comboCount)
                .build();
    }
}
