package com.fashionapp.domain.clothes;

import com.fashionapp.domain.combo.ComboTestSupport;
import com.fashionapp.domain.event.EventRecorder;
import com.fashionapp.domain.user.UserRepository;
import com.fashionapp.domain.wear.WearRecordRepository;
import com.fashionapp.domain.wear.WearStat;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.infra.AiServerClient;
import com.fashionapp.infra.ImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** FR-27: PATCH /api/clothes/status and status/available in the clothes list; combo counts ignore both. */
@ExtendWith(MockitoExtension.class)
class ClothesStatusServiceTest {

    @Mock private ClothesRepository clothesRepository;
    @Mock private UserRepository userRepository;
    @Mock private AiServerClient aiServerClient;
    @Mock private ImageStorage imageStorage;
    @Mock private ClothesEmbedder clothesEmbedder;
    @Mock private WearRecordRepository wearRecordRepository;

    private ClothesService clothesService;
    private final UUID userId = UUID.randomUUID();

    private static Clothes piece(String category) {
        return Clothes.builder().id(UUID.randomUUID()).imageUrl("clothes/x.jpg").category(category).color("블랙")
                .status("AVAILABLE").build();
    }

    @BeforeEach
    void setUp() {
        clothesService = new ClothesService(clothesRepository, userRepository, new ClothesClassifier(aiServerClient),
                imageStorage, clothesEmbedder, ComboTestSupport.defaultService(), wearRecordRepository,
                mock(EventRecorder.class), ClothesAvailabilityTest.defaultAvailability());
    }

    @Test
    @DisplayName("여러 벌을 한 번에 LAUNDRY로 바꾸고 종료 시각은 3일 뒤")
    void changeToLaundry() {
        Clothes a = piece("TOP");
        Clothes b = piece("BOTTOM");
        given(clothesRepository.findByUser_IdAndIdIn(userId, Set.of(a.getId(), b.getId()))).willReturn(List.of(a, b));

        ClothesStatusResponse res = clothesService.changeStatus(userId,
                new ClothesStatusRequest(List.of(a.getId().toString(), b.getId().toString()), "LAUNDRY"));

        assertThat(res.status()).isEqualTo(ClothesStatus.LAUNDRY);
        assertThat(res.statusUntil()).isEqualTo(ClothesAvailabilityTest.NOW.plusDays(3));
        assertThat(res.clothesIds()).containsExactly(a.getId(), b.getId());
        assertThat(List.of(a, b)).allSatisfy(c -> {
            assertThat(c.getStatus()).isEqualTo("LAUNDRY");
            assertThat(c.getStatusUntil()).isEqualTo(ClothesAvailabilityTest.NOW.plusDays(3));
        });
        verify(clothesRepository).saveAll(List.of(a, b));
    }

    @Test
    @DisplayName("AVAILABLE로 되돌리면 종료 시각을 지운다")
    void changeBackToAvailable() {
        Clothes a = piece("TOP");
        a.changeStatus(ClothesStatus.LAUNDRY, ClothesAvailabilityTest.NOW.plusDays(3));
        given(clothesRepository.findByUser_IdAndIdIn(userId, Set.of(a.getId()))).willReturn(List.of(a));

        ClothesStatusResponse res = clothesService.changeStatus(userId,
                new ClothesStatusRequest(List.of(a.getId().toString()), "available"));

        assertThat(res.status()).isEqualTo(ClothesStatus.AVAILABLE);
        assertThat(res.statusUntil()).isNull();
        assertThat(a.getStatus()).isEqualTo("AVAILABLE");
        assertThat(a.getStatusUntil()).isNull();
    }

    @Test
    @DisplayName("남의 옷 ID가 섞이면 400(INVALID_STATUS_CLOTHES)이고 아무것도 바꾸지 않는다")
    void foreignIdRejected() {
        Clothes mine = piece("TOP");
        UUID theirs = UUID.randomUUID();
        given(clothesRepository.findByUser_IdAndIdIn(userId, Set.of(mine.getId(), theirs))).willReturn(List.of(mine));

        assertThatThrownBy(() -> clothesService.changeStatus(userId,
                new ClothesStatusRequest(List.of(mine.getId().toString(), theirs.toString()), "LAUNDRY")))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATUS_CLOTHES);
        assertThat(mine.getStatus()).isEqualTo("AVAILABLE");
        assertThat(mine.getStatusUntil()).isNull();
        verify(clothesRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("빈 목록·잘못된 ID·모르는 상태는 400(INVALID_CLOTHES_STATUS_REQUEST)")
    void invalidRequests() {
        String id = UUID.randomUUID().toString();
        for (ClothesStatusRequest request : List.of(
                new ClothesStatusRequest(List.of(), "LAUNDRY"),
                new ClothesStatusRequest(null, "LAUNDRY"),
                new ClothesStatusRequest(List.of("not-a-uuid"), "LAUNDRY"),
                new ClothesStatusRequest(List.of(id), "DRY_CLEANING"),
                new ClothesStatusRequest(List.of(id), null))) {
            assertThatThrownBy(() -> clothesService.changeStatus(userId, request))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_CLOTHES_STATUS_REQUEST);
        }
        verify(clothesRepository, never()).findByUser_IdAndIdIn(any(), any());
    }

    @Test
    @DisplayName("옷 목록: 세탁 중은 LAUNDRY·false, 어제 입은 상의는 AVAILABLE·false, 4일 전 상의와 어제 신발은 true")
    void listStatusAndAvailable() {
        Clothes laundry = piece("BOTTOM");
        laundry.changeStatus(ClothesStatus.LAUNDRY, ClothesAvailabilityTest.NOW.plusDays(3));
        Clothes wornYesterday = piece("TOP");
        Clothes wornFourDaysAgo = piece("TOP");
        Clothes shoesYesterday = piece("SHOES");
        given(clothesRepository.findByUser_IdOrderByCreatedAtDesc(userId))
                .willReturn(List.of(laundry, wornYesterday, wornFourDaysAgo, shoesYesterday));
        given(wearRecordRepository.findStatsByUserId(userId)).willReturn(List.of(
                new WearStat(wornYesterday.getId(), 1, ClothesAvailabilityTest.TODAY.minusDays(1)),
                new WearStat(wornFourDaysAgo.getId(), 1, ClothesAvailabilityTest.TODAY.minusDays(4)),
                new WearStat(shoesYesterday.getId(), 1, ClothesAvailabilityTest.TODAY.minusDays(1))));

        Map<UUID, ClothesResponse> byId = clothesService.getMyClothes(userId).stream()
                .collect(Collectors.toMap(ClothesResponse::getId, Function.identity()));

        assertThat(byId.get(laundry.getId()).getStatus()).isEqualTo(ClothesStatus.LAUNDRY);
        assertThat(byId.get(laundry.getId()).isAvailable()).isFalse();
        assertThat(byId.get(wornYesterday.getId()).getStatus()).isEqualTo(ClothesStatus.AVAILABLE);
        assertThat(byId.get(wornYesterday.getId()).isAvailable()).isFalse();
        assertThat(byId.get(wornFourDaysAgo.getId()).isAvailable()).isTrue();
        assertThat(byId.get(shoesYesterday.getId()).isAvailable()).isTrue();
    }

    @Test
    @DisplayName("코디 수(FR-09)는 세탁 중·쿨다운과 무관하게 옷장 전체로 센다")
    void comboCountIgnoresStatus() {
        Clothes top = piece("TOP");
        Clothes bottom = piece("BOTTOM");
        Clothes shoes = piece("SHOES");
        given(clothesRepository.findByUser_IdOrderByCreatedAtDesc(userId)).willReturn(List.of(top, bottom, shoes));
        int before = clothesService.getSummary(userId).comboCount();

        top.changeStatus(ClothesStatus.LAUNDRY, ClothesAvailabilityTest.NOW.plusDays(3));
        given(wearRecordRepository.findStatsByUserId(userId)).willReturn(List.of(
                new WearStat(bottom.getId(), 1, ClothesAvailabilityTest.TODAY.minusDays(1))));

        assertThat(before).isEqualTo(1);
        assertThat(clothesService.getSummary(userId).comboCount()).isEqualTo(before);
        assertThat(clothesService.getMyClothes(userId)).allSatisfy(c -> assertThat(c.getComboCount()).isEqualTo(1));
    }
}
