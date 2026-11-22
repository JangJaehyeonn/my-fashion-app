package com.fashionapp.domain.wear;

import com.fashionapp.domain.clothes.Clothes;
import com.fashionapp.domain.clothes.ClothesRepository;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** FR-10 wear log: record worn clothes per day, per-piece stats for the clothes list, and the week/month summary. */
@Service
@RequiredArgsConstructor
public class WearService {

    /** Most worn first; ties go to the more recently worn piece, then the smaller id (deterministic). */
    static final Comparator<WearStat> MOST_WORN = Comparator.comparingLong(WearStat::count).reversed()
            .thenComparing(WearStat::lastWornOn, Comparator.reverseOrder())
            .thenComparing(WearStat::clothesId);

    private final WearRecordRepository wearRecordRepository;
    private final ClothesRepository clothesRepository;
    private final Clock clock;

    /** All ids must be the user's own clothes, otherwise 400 and nothing is stored. */
    @Transactional
    public WearRecordResponse record(UUID userId, WearRequest request) {
        Set<UUID> ids = parseIds(request.getClothesIds());
        LocalDate today = WearCalendar.today(clock);
        LocalDate day = parseDate(request.getDate(), today);
        if (clothesRepository.findByUser_IdAndIdIn(userId, ids).size() != ids.size()) {
            throw new CustomException(ErrorCode.INVALID_WEAR_CLOTHES);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        int inserted = 0;
        for (UUID id : ids) {
            inserted += wearRecordRepository.insertIgnoringDuplicate(userId, id, day, now);
        }
        return new WearRecordResponse(day, inserted);
    }

    /** Per-piece stats keyed by clothes id; pieces never worn are absent. */
    @Transactional(readOnly = true)
    public Map<UUID, WearStat> statsFor(UUID userId) {
        return wearRecordRepository.findStatsByUserId(userId).stream()
                .collect(Collectors.toMap(WearStat::clothesId, Function.identity()));
    }

    /** FR-11: per-piece stats for days in [from, to] keyed by clothes id; pieces not worn then are absent. */
    @Transactional(readOnly = true)
    public Map<UUID, WearStat> statsBetween(UUID userId, LocalDate from, LocalDate to) {
        return wearRecordRepository.findStatsByUserIdBetween(userId, from, to).stream()
                .collect(Collectors.toMap(WearStat::clothesId, Function.identity()));
    }

    /** FR-11: the first day the user recorded a wear (empty without records). */
    @Transactional(readOnly = true)
    public Optional<LocalDate> firstWornOn(UUID userId) {
        return Optional.ofNullable(wearRecordRepository.findFirstWornOn(userId));
    }

    @Transactional(readOnly = true)
    public WearSummaryResponse summary(UUID userId) {
        LocalDate today = WearCalendar.today(clock);
        long week = wearRecordRepository.countByUserIdAndWornOnBetween(userId, WearCalendar.weekStart(today), today);
        long month = wearRecordRepository.countByUserIdAndWornOnBetween(userId, WearCalendar.monthStart(today), today);
        return new WearSummaryResponse(week, month, mostWorn(userId).orElse(null));
    }

    private Optional<WearSummaryResponse.MostWorn> mostWorn(UUID userId) {
        return wearRecordRepository.findStatsByUserId(userId).stream()
                .min(MOST_WORN)
                .flatMap(stat -> clothesRepository.findByIdAndUser_Id(stat.clothesId(), userId))
                .map(WearService::toMostWorn);
    }

    private static WearSummaryResponse.MostWorn toMostWorn(Clothes clothes) {
        return new WearSummaryResponse.MostWorn(clothes.getId(), clothes.getName());
    }

    private static Set<UUID> parseIds(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_WEAR_REQUEST);
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (String value : raw) {
            try {
                ids.add(UUID.fromString(value));
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new CustomException(ErrorCode.INVALID_WEAR_REQUEST);
            }
        }
        return ids;
    }

    /** Omitted means today. A future day cannot have been worn yet. */
    private static LocalDate parseDate(String raw, LocalDate today) {
        if (raw == null || raw.isBlank()) {
            return today;
        }
        try {
            LocalDate day = LocalDate.parse(raw.trim());
            if (day.isAfter(today)) {
                throw new CustomException(ErrorCode.INVALID_WEAR_REQUEST);
            }
            return day;
        } catch (DateTimeParseException e) {
            throw new CustomException(ErrorCode.INVALID_WEAR_REQUEST);
        }
    }
}
