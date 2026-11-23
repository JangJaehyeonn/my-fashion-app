package com.fashionapp.domain.outfit;

import com.fashionapp.domain.clothes.ClothesCategory;
import com.fashionapp.domain.clothes.ClothesResponse;
import com.fashionapp.domain.combo.Combo;
import com.fashionapp.domain.combo.ComboEngine;
import com.fashionapp.domain.combo.ComboItem;
import com.fashionapp.domain.wear.WearCalendar;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Rule side of the closet recommendation: the valid combos sent to the AI as candidates and, once per week per
 * user, the FR-29 revival (a long-unworn piece's combo put first). Deterministic: time comes from the injected clock.
 */
@Component
public class ClosetPlanner {

    // Same cap as the AI server accepts for candidates (ai-server MAX_CANDIDATES)
    static final int MAX_CANDIDATES = 50;

    private final ComboEngine engine;
    private final Clock clock;
    private final int unwornDays;

    public ClosetPlanner(ComboEngine engine, Clock clock,
                         @Value("${wearon.revival.unworn-days:60}") int unwornDays) {
        if (unwornDays <= 0) {
            throw new IllegalArgumentException("wearon.revival.unworn-days must be positive: " + unwornDays);
        }
        this.engine = engine;
        this.clock = clock;
        this.unwornDays = unwornDays;
    }

    /** One long-unworn piece and the valid combo it is shown in. */
    public record Revival(UUID clothesId, Combo combo) {
    }

    /** Candidates (revived combo first when present) and the revival, or null when none this time. */
    public record ClosetPlan(List<Combo> candidates, Revival revival) {
    }

    public LocalDate today() {
        return WearCalendar.today(clock);
    }

    /** @param available pieces I can wear now (FR-27); lastRevivedOn the user's last revival day, null if never */
    public ClosetPlan plan(List<ClothesResponse> available, LocalDate lastRevivedOn) {
        return plan(available, lastRevivedOn, combo -> true);
    }

    /**
     * Same as {@link #plan(List, LocalDate)} but only combos accepted by {@code allowed} (FR-28 weather rules) are
     * candidates, so the revived combo also obeys them.
     */
    public ClosetPlan plan(List<ClothesResponse> available, LocalDate lastRevivedOn, Predicate<Combo> allowed) {
        List<Combo> combos = engine.combos(available.stream().map(ComboItem::of).toList()).stream()
                .filter(allowed)
                .toList();
        LocalDate today = today();
        Revival revival = revivalDue(lastRevivedOn, today) ? pickRevival(available, combos, today).orElse(null) : null;
        Stream<Combo> ordered = revival == null ? combos.stream()
                : Stream.concat(Stream.of(revival.combo()), combos.stream().filter(c -> !c.equals(revival.combo())));
        return new ClosetPlan(ordered.limit(MAX_CANDIDATES).toList(), revival);
    }

    /** Once per calendar week (Monday start, Korean day — FR-10 WearCalendar). */
    static boolean revivalDue(LocalDate lastRevivedOn, LocalDate today) {
        return lastRevivedOn == null || WearCalendar.weekStart(lastRevivedOn).isBefore(WearCalendar.weekStart(today));
    }

    /** Longest-unworn piece first (ties by id) that is in at least one valid combo; its first combo in engine order. */
    Optional<Revival> pickRevival(List<ClothesResponse> available, List<Combo> combos, LocalDate today) {
        return available.stream()
                .filter(c -> isLongUnworn(c, today))
                .sorted(Comparator.comparing(ClosetPlanner::unwornSince).thenComparing(ClothesResponse::getId))
                .map(c -> firstComboWith(c.getId(), combos).map(combo -> new Revival(c.getId(), combo)))
                .flatMap(Optional::stream)
                .findFirst();
    }

    /** Classified and no wear (or, never worn, no registration) within the last {@code unwornDays} days. */
    boolean isLongUnworn(ClothesResponse clothes, LocalDate today) {
        if (clothes.getId() == null || clothes.getCategory() == null
                || clothes.getCategory() == ClothesCategory.UNCLASSIFIED) {
            return false;
        }
        LocalDate since = unwornSince(clothes);
        return since != null && ChronoUnit.DAYS.between(since, today) >= unwornDays;
    }

    private static LocalDate unwornSince(ClothesResponse clothes) {
        if (clothes.getLastWornAt() != null) {
            return clothes.getLastWornAt();
        }
        return clothes.getCreatedAt() == null ? null : clothes.getCreatedAt().toLocalDate();
    }

    private static Optional<Combo> firstComboWith(UUID clothesId, List<Combo> combos) {
        return combos.stream()
                .filter(combo -> combo.items().stream().anyMatch(item -> Objects.equals(item.id(), clothesId)))
                .findFirst();
    }

    static List<UUID> idsOf(Combo combo) {
        List<UUID> ids = new ArrayList<>();
        combo.items().forEach(item -> ids.add(item.id()));
        return ids;
    }
}
