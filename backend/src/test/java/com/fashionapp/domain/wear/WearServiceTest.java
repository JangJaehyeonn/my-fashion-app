package com.fashionapp.domain.wear;

import com.fashionapp.domain.clothes.Clothes;
import com.fashionapp.domain.clothes.ClothesRepository;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** FR-10: wear logging rules (ownership, one per piece per day, KST today, Monday weeks, most worn). */
@ExtendWith(MockitoExtension.class)
class WearServiceTest {

    // 2026-11-22 (Sunday) 15:30 UTC = 2026-11-23 (Monday) 00:30 KST
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-22T15:30:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

    @Mock private WearRecordRepository wearRecordRepository;
    @Mock private ClothesRepository clothesRepository;
    @Mock private com.fashionapp.domain.event.EventRecorder eventRecorder;

    private WearService wearService;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        wearService = new WearService(wearRecordRepository, clothesRepository, CLOCK, eventRecorder);
    }

    private static Clothes clothes(UUID id, String name) {
        return Clothes.builder().id(id).imageUrl("clothes/x.jpg").category("TOP").name(name).build();
    }

    private void owns(UUID... ids) {
        List<Clothes> owned = java.util.Arrays.stream(ids).map(id -> clothes(id, "옷")).toList();
        given(clothesRepository.findByUser_IdAndIdIn(eq(userId), anyCollection())).willReturn(owned);
    }

    @Test
    @DisplayName("날짜 없이 기록하면 한국 날짜 오늘로 옷마다 한 번씩 넣고, 요청 안의 중복 ID는 한 번만 넣는다")
    void recordsTodayInKoreaOncePerPiece() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        owns(a, b, c);
        given(wearRecordRepository.insertIgnoringDuplicate(eq(userId), any(), eq(TODAY), any())).willReturn(1);

        WearRecordResponse res = wearService.record(userId,
                new WearRequest(List.of(a.toString(), b.toString(), c.toString(), a.toString()), null));

        assertThat(res.date()).isEqualTo(TODAY);
        assertThat(res.recorded()).isEqualTo(3);
        verify(clothesRepository).findByUser_IdAndIdIn(userId, Set.of(a, b, c));
        verify(wearRecordRepository, times(3)).insertIgnoringDuplicate(eq(userId), any(), eq(TODAY), any());
    }

    @Test
    @DisplayName("이미 그날 기록된 옷은 저장소가 무시하므로 새로 센 기록은 0이다")
    void duplicateDayIsNotCountedAgain() {
        UUID a = UUID.randomUUID();
        owns(a);
        given(wearRecordRepository.insertIgnoringDuplicate(userId, a, TODAY.minusDays(3), LocalDate.of(2026, 11, 22)
                .atTime(15, 30))).willReturn(0);

        WearRecordResponse res = wearService.record(userId, new WearRequest(List.of(a.toString()), "2026-11-20"));

        assertThat(res.date()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(res.recorded()).isZero();
    }

    @Test
    @DisplayName("남의 옷 ID가 섞이면 400이고 아무것도 기록하지 않는다")
    void otherUsersClothesRejected() {
        UUID mine = UUID.randomUUID();
        UUID theirs = UUID.randomUUID();
        owns(mine);

        assertThatThrownBy(() -> wearService.record(userId,
                new WearRequest(List.of(mine.toString(), theirs.toString()), null)))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_WEAR_CLOTHES);
        verify(wearRecordRepository, never()).insertIgnoringDuplicate(any(), any(), any(), any());
    }

    @Test
    @DisplayName("빈 목록·잘못된 ID·잘못된 날짜·미래 날짜는 400이고 기록하지 않는다")
    void invalidRequestsRejected() {
        String id = UUID.randomUUID().toString();
        List<WearRequest> invalid = List.of(
                new WearRequest(null, null),
                new WearRequest(List.of(), null),
                new WearRequest(List.of("not-a-uuid"), null),
                new WearRequest(List.of(id), "2026/11/20"),
                new WearRequest(List.of(id), TODAY.plusDays(1).toString()));
        for (WearRequest request : invalid) {
            assertThatThrownBy(() -> wearService.record(userId, request))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_WEAR_REQUEST);
        }
        verify(wearRecordRepository, never()).insertIgnoringDuplicate(any(), any(), any(), any());
    }

    @Test
    @DisplayName("요약: 이번 주는 월요일부터, 이번 달은 1일부터 오늘까지 세고 가장 많이 입은 옷을 준다")
    void summaryRangesAndMostWorn() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        // KST today is Monday 2026-11-23, so the week is just today
        given(wearRecordRepository.countByUserIdAndWornOnBetween(userId, TODAY, TODAY)).willReturn(1L);
        given(wearRecordRepository.countByUserIdAndWornOnBetween(userId, LocalDate.of(2026, 11, 1), TODAY)).willReturn(4L);
        given(wearRecordRepository.findStatsByUserId(userId)).willReturn(List.of(
                new WearStat(b, 2, TODAY.minusDays(1)),
                new WearStat(a, 3, TODAY.minusDays(10))));
        given(clothesRepository.findByIdAndUser_Id(a, userId)).willReturn(Optional.of(clothes(a, "네이비 니트")));

        WearSummaryResponse res = wearService.summary(userId);

        assertThat(res.weekCount()).isEqualTo(1);
        assertThat(res.monthCount()).isEqualTo(4);
        assertThat(res.mostWorn().id()).isEqualTo(a);
        assertThat(res.mostWorn().name()).isEqualTo("네이비 니트");
    }

    @Test
    @DisplayName("가장 많이 입은 옷이 같은 횟수면 최근에 입은 옷, 그다음 id가 작은 옷")
    void mostWornTieBreak() {
        UUID small = new UUID(0, 1);
        UUID large = new UUID(0, 2);
        UUID recent = new UUID(0, 3);
        List<WearStat> stats = List.of(
                new WearStat(large, 2, TODAY.minusDays(5)),
                new WearStat(recent, 2, TODAY),
                new WearStat(small, 2, TODAY.minusDays(5)));

        assertThat(stats.stream().min(WearService.MOST_WORN).orElseThrow().clothesId()).isEqualTo(recent);
        assertThat(stats.stream().filter(s -> !s.clothesId().equals(recent)).min(WearService.MOST_WORN)
                .orElseThrow().clothesId()).isEqualTo(small);
    }

    @Test
    @DisplayName("기록이 없으면 횟수는 0이고 가장 많이 입은 옷은 null")
    void emptySummary() {
        given(wearRecordRepository.findStatsByUserId(userId)).willReturn(List.of());

        WearSummaryResponse res = wearService.summary(userId);

        assertThat(res.weekCount()).isZero();
        assertThat(res.monthCount()).isZero();
        assertThat(res.mostWorn()).isNull();
    }

    @Test
    @DisplayName("주 시작은 월요일: 일요일의 이번 주는 6일 전 월요일부터")
    void weekStartsOnMonday() {
        assertThat(WearCalendar.weekStart(LocalDate.of(2026, 11, 22))).isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(WearCalendar.weekStart(LocalDate.of(2026, 11, 23))).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(WearCalendar.today(CLOCK)).isEqualTo(TODAY);
    }
}
