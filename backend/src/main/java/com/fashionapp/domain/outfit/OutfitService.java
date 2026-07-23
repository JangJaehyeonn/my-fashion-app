package com.fashionapp.domain.outfit;

import com.fashionapp.domain.clothes.Clothes;
import com.fashionapp.domain.clothes.ClothesRepository;
import com.fashionapp.domain.user.User;
import com.fashionapp.domain.user.UserRepository;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.infra.AiRecommendRequest;
import com.fashionapp.infra.AiRecommendResponse;
import com.fashionapp.infra.AiServerClient;
import com.fashionapp.infra.AiSituationRecommendRequest;
import com.fashionapp.infra.AiSituationRecommendResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutfitService {

    private final OutfitRepository outfitRepository;
    private final OutfitCalendarRepository outfitCalendarRepository;
    private final ClothesRepository clothesRepository;
    private final UserRepository userRepository;
    private final AiServerClient aiServerClient;

    @Transactional(readOnly = true)
    public List<OutfitResponse> getMyOutfits(UUID userId) {
        return outfitRepository.findByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(OutfitResponse::from)
                .toList();
    }

    @Transactional
    public OutfitResponse createOutfit(UUID userId, OutfitCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Outfit outfit = Outfit.builder()
                .user(user)
                .name(request.getName())
                .styleTag(request.getStyleTag())
                .weatherCondition(request.getWeatherCondition())
                .build();

        outfitRepository.save(outfit);

        if (request.getClothesIds() != null) {
            for (UUID clothesId : request.getClothesIds()) {
                Clothes clothes = clothesRepository.findByIdAndUser_Id(clothesId, userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.CLOTHES_NOT_FOUND));
                outfit.getItems().add(OutfitItem.builder()
                        .outfit(outfit)
                        .clothes(clothes)
                        .build());
            }
        }

        return OutfitResponse.from(outfit);
    }

    @Transactional
    public void deleteOutfit(UUID userId, UUID outfitId) {
        Outfit outfit = outfitRepository.findByIdAndUser_Id(outfitId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.OUTFIT_NOT_FOUND));
        outfitRepository.delete(outfit);
    }

    @Transactional(readOnly = true)
    public List<OutfitCalendarResponse> getMonthlyCalendar(UUID userId, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        return outfitCalendarRepository
                .findByUser_IdAndWornDateBetweenOrderByWornDateAsc(userId, start, end)
                .stream()
                .map(OutfitCalendarResponse::from)
                .toList();
    }

    @Transactional
    public OutfitCalendarResponse addCalendarEntry(UUID userId, OutfitCalendarCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Outfit outfit = outfitRepository.findByIdAndUser_Id(request.getOutfitId(), userId)
                .orElseThrow(() -> new CustomException(ErrorCode.OUTFIT_NOT_FOUND));

        OutfitCalendar calendar = OutfitCalendar.builder()
                .user(user)
                .outfit(outfit)
                .wornDate(request.getWornDate())
                .memo(request.getMemo())
                .build();

        return OutfitCalendarResponse.from(outfitCalendarRepository.save(calendar));
    }

    @Transactional(readOnly = true)
    public AiRecommendResponse recommend(UUID userId, double temperature, String condition) {
        List<Clothes> clothes = clothesRepository.findByUser_IdOrderByCreatedAtDesc(userId);

        List<AiRecommendRequest.ClothesItem> clothesItems = clothes.stream()
                .map(c -> new AiRecommendRequest.ClothesItem(
                        c.getId().toString(), c.getCategory(), c.getColor(),
                        c.getPattern(), c.getSeason(), c.getStyleTag()))
                .toList();

        AiRecommendRequest request = new AiRecommendRequest(
                new AiRecommendRequest.WeatherInfo(temperature, condition),
                clothesItems
        );

        return aiServerClient.recommendOutfits(request);
    }

    @Transactional(readOnly = true)
    public AiSituationRecommendResponse recommendBySituation(UUID userId, SituationRecommendRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        AiSituationRecommendRequest.BodyProfile bodyProfile = new AiSituationRecommendRequest.BodyProfile(
                user.getHeight(),
                user.getWeight(),
                user.getBodyType() != null ? user.getBodyType().name() : null,
                user.getPreferredStyle() != null ? user.getPreferredStyle().name() : null
        );

        AiSituationRecommendRequest aiRequest = new AiSituationRecommendRequest(
                new AiSituationRecommendRequest.WeatherInfo(request.getTemperature(), request.getCondition()),
                request.getSituation(),
                bodyProfile
        );

        return aiServerClient.recommendOutfitsBySituation(aiRequest);
    }

    @Transactional
    public void deleteCalendarEntry(UUID userId, UUID calendarId) {
        OutfitCalendar calendar = outfitCalendarRepository.findByIdAndUser_Id(calendarId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CALENDAR_NOT_FOUND));
        outfitCalendarRepository.delete(calendar);
    }
}
