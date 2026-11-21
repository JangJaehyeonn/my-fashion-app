package com.fashionapp.domain.outfit;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OutfitService {

    // 상의 하나만 있는 조합처럼 코디로 보기 어려운 결과는 버림
    private static final int MIN_ITEMS_PER_OUTFIT = 2;

    private final UserRepository userRepository;
    private final AiServerClient aiServerClient;
    private final ClothesService clothesService;

    // AI 서버 호출(수 초~수 분)을 기다리는 동안 DB 커넥션을 붙잡지 않도록 트랜잭션을 걸지 않음 —
    // 조회·저장은 각 리포지토리 호출이 짧은 트랜잭션으로 처리 (open-in-view도 꺼져 있어야 함, application.yml)
    public AiSituationRecommendResponse recommendBySituation(UUID userId, SituationRecommendRequest request) {
        User user = findUser(userId);

        AiSituationRecommendRequest aiRequest = new AiSituationRecommendRequest(
                toWeatherInfo(request),
                request.getSituation(),
                toBodyProfile(user)
        );

        return join(aiServerClient.recommendOutfitsBySituation(aiRequest));
    }

    // AI 서버 호출(수 초~수 분)을 기다리는 동안 DB 커넥션을 붙잡지 않도록 트랜잭션을 걸지 않음 —
    // 조회·저장은 각 리포지토리 호출이 짧은 트랜잭션으로 처리 (open-in-view도 꺼져 있어야 함, application.yml)
    public ClosetRecommendResponse recommendByCloset(UUID userId, SituationRecommendRequest request) {
        User user = findUser(userId);

        List<ClothesResponse> closet = clothesService.getMyClothes(userId);
        if (closet.isEmpty()) {
            throw new CustomException(ErrorCode.CLOSET_EMPTY);
        }

        List<AiClosetRecommendRequest.ClosetItem> items = closet.stream()
                .map(c -> new AiClosetRecommendRequest.ClosetItem(
                        c.getId().toString(), c.getCategory().name(), c.getColor(), c.getName()))
                .toList();

        AiClosetRecommendResponse aiResult = join(aiServerClient.recommendOutfitsByCloset(
                new AiClosetRecommendRequest(toWeatherInfo(request), request.getSituation(), toBodyProfile(user), items)
        ));

        // AI 서버에서도 걸러내지만, 응답 ID가 내 옷장에 실제로 있는지 여기서 한 번 더 검증
        Map<String, ClothesResponse> byId = closet.stream()
                .collect(Collectors.toMap(c -> c.getId().toString(), Function.identity()));

        List<ClosetRecommendResponse.ClosetOutfit> outfits = aiResult.getOutfits() == null ? List.of() :
                aiResult.getOutfits().stream()
                        .map(o -> new ClosetRecommendResponse.ClosetOutfit(
                                o.getClothesIds() == null ? List.of() :
                                        o.getClothesIds().stream().distinct().map(byId::get).filter(Objects::nonNull).toList(),
                                o.getReason(),
                                o.getStyleTag()))
                        .filter(o -> o.getItems().size() >= MIN_ITEMS_PER_OUTFIT)
                        .toList();

        return new ClosetRecommendResponse(outfits);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private AiSituationRecommendRequest.WeatherInfo toWeatherInfo(SituationRecommendRequest request) {
        return new AiSituationRecommendRequest.WeatherInfo(request.getTemperature(), request.getCondition());
    }

    private AiSituationRecommendRequest.BodyProfile toBodyProfile(User user) {
        return new AiSituationRecommendRequest.BodyProfile(
                user.getHeight(),
                user.getWeight(),
                user.getBodyType() != null ? user.getBodyType().name() : null,
                user.getPreferredStyleNames().stream().findFirst().orElse(null),
                user.getPreferredStyleNames()
        );
    }

    // 비동기 호출 내부의 CustomException을 꺼내 GlobalExceptionHandler가 원래 상태코드로 처리하게 함
    private <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof CustomException customException) {
                throw customException;
            }
            throw e;
        }
    }
}
