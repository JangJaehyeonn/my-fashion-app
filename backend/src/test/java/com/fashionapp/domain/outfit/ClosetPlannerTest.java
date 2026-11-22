package com.fashionapp.domain.outfit;

import com.fashionapp.domain.clothes.ClothesCategory;
import com.fashionapp.domain.clothes.ClothesResponse;
import com.fashionapp.domain.combo.ComboTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FR-29 rules: long-unworn pieces, once a week, only when the piece is in a valid combo. */
class ClosetPlannerTest {

    // 2026-11-25 (Wednesday) 12:00 KST
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-25T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    private final ClosetPlanner planner = new ClosetPlanner(ComboTestSupport.defaultEngine(), CLOCK, 60);

    private static UUID id(int n) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(n));
    }

    private static ClothesResponse piece(int n, ClothesCategory category, String color, LocalDate lastWorn,
                                         LocalDate created) {
        return ClothesResponse.builder().id(id(n)).category(category).color(color).lastWornAt(lastWorn)
                .createdAt(created == null ? null : created.atStartOfDay()).available(true).build();
    }

    private static List<ClothesResponse> closet(ClothesResponse top) {
        List<ClothesResponse> closet = new ArrayList<>();
        closet.add(piece(1, ClothesCategory.TOP, "블랙", TODAY.minusDays(5), TODAY));
        closet.add(top);
        closet.add(piece(3, ClothesCategory.BOTTOM, "블랙", TODAY.minusDays(5), TODAY));
        closet.add(piece(4, ClothesCategory.SHOES, "화이트", TODAY.minusDays(5), TODAY));
        return closet;
    }

    @Test
    @DisplayName("마지막 착용이 60일 전이면 오래 안 입은 옷, 59일 전이면 아니다")
    void longUnworn_boundary() {
        assertThat(planner.isLongUnworn(piece(2, ClothesCategory.TOP, "화이트", TODAY.minusDays(60), null), TODAY)).isTrue();
        assertThat(planner.isLongUnworn(piece(2, ClothesCategory.TOP, "화이트", TODAY.minusDays(59), null), TODAY)).isFalse();
    }

    @Test
    @DisplayName("착용 기록이 없으면 등록일로 보고, 미분류 옷은 대상이 아니다")
    void longUnworn_neverWornUsesCreatedAt_unclassifiedExcluded() {
        assertThat(planner.isLongUnworn(piece(2, ClothesCategory.TOP, "화이트", null, TODAY.minusDays(61)), TODAY)).isTrue();
        assertThat(planner.isLongUnworn(piece(2, ClothesCategory.TOP, "화이트", null, TODAY.minusDays(10)), TODAY)).isFalse();
        assertThat(planner.isLongUnworn(piece(2, ClothesCategory.UNCLASSIFIED, null, null, TODAY.minusDays(90)), TODAY)).isFalse();
    }

    @Test
    @DisplayName("오래 안 입은 옷이 든 조합이 후보 맨 앞에 오고 나머지는 엔진 순서대로 중복 없이 따라온다")
    void plan_revivedComboFirst() {
        ClosetPlanner.ClosetPlan plan = planner.plan(
                closet(piece(2, ClothesCategory.TOP, "화이트", TODAY.minusDays(70), TODAY.minusDays(90))), null);

        assertThat(plan.revival()).isNotNull();
        assertThat(plan.revival().clothesId()).isEqualTo(id(2));
        assertThat(plan.candidates()).hasSize(2);
        assertThat(ClosetPlanner.idsOf(plan.candidates().get(0))).containsExactly(id(2), id(3), id(4));
        assertThat(ClosetPlanner.idsOf(plan.candidates().get(1))).containsExactly(id(1), id(3), id(4));
    }

    @Test
    @DisplayName("그 옷이 든 유효 조합이 없으면(기타 옷, 색 충돌) revival이 없다")
    void plan_noValidCombo_noRevival() {
        List<ClothesResponse> withBag = closet(piece(2, ClothesCategory.TOP, "블랙", TODAY.minusDays(5), TODAY));
        withBag.add(piece(5, ClothesCategory.ETC, "브라운", null, TODAY.minusDays(90)));
        assertThat(planner.plan(withBag, null).revival()).isNull();

        List<ClothesResponse> conflict = closet(piece(2, ClothesCategory.TOP, "레드", TODAY.minusDays(70), null));
        conflict.set(2, piece(3, ClothesCategory.BOTTOM, "그린", TODAY.minusDays(5), TODAY));
        ClosetPlanner.ClosetPlan plan = planner.plan(conflict, null);
        assertThat(plan.revival()).isNull();
        assertThat(plan.candidates()).allSatisfy(c -> assertThat(ClosetPlanner.idsOf(c)).doesNotContain(id(2)));
    }

    @Test
    @DisplayName("같은 주(월요일 시작)에 노출했으면 다시 넣지 않고, 지난주에 노출했으면 넣는다")
    void revivalDue_oncePerCalendarWeek() {
        LocalDate monday = LocalDate.of(2026, 11, 23);
        assertThat(ClosetPlanner.revivalDue(null, TODAY)).isTrue();
        assertThat(ClosetPlanner.revivalDue(monday, TODAY)).isFalse();
        assertThat(ClosetPlanner.revivalDue(monday.minusDays(1), TODAY)).isTrue();

        ClosetPlanner.ClosetPlan plan = planner.plan(
                closet(piece(2, ClothesCategory.TOP, "화이트", TODAY.minusDays(70), null)), monday);
        assertThat(plan.revival()).isNull();
    }

    @Test
    @DisplayName("가장 오래 안 입은 옷부터 고른다")
    void pickRevival_longestUnwornFirst() {
        List<ClothesResponse> closet = closet(piece(2, ClothesCategory.TOP, "화이트", TODAY.minusDays(70), null));
        closet.set(0, piece(1, ClothesCategory.TOP, "블랙", TODAY.minusDays(100), null));
        assertThat(planner.plan(closet, null).revival().clothesId()).isEqualTo(id(1));
    }

    @Test
    @DisplayName("후보는 최대 50개")
    void plan_capsCandidates() {
        List<ClothesResponse> big = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            big.add(piece(10 + i, ClothesCategory.TOP, "블랙", TODAY.minusDays(5), TODAY));
            big.add(piece(20 + i, ClothesCategory.BOTTOM, "블랙", TODAY.minusDays(5), TODAY));
        }
        big.add(piece(30, ClothesCategory.SHOES, "블랙", TODAY.minusDays(5), TODAY));
        assertThat(planner.plan(big, null).candidates()).hasSize(ClosetPlanner.MAX_CANDIDATES);
    }

    @Test
    @DisplayName("설정 일수가 0 이하면 기동 실패")
    void rejectsNonPositiveDays() {
        assertThatThrownBy(() -> new ClosetPlanner(ComboTestSupport.defaultEngine(), CLOCK, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
