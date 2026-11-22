package com.fashionapp.domain.purchase;

import com.fashionapp.domain.clothes.Clothes;
import com.fashionapp.domain.clothes.ClothesCategory;
import com.fashionapp.domain.clothes.ClothesClassification;
import com.fashionapp.domain.clothes.ClothesClassifier;
import com.fashionapp.domain.clothes.ClothesEmbedder;
import com.fashionapp.domain.clothes.ClothesEmbeddingStore;
import com.fashionapp.domain.clothes.ClothesRepository;
import com.fashionapp.domain.combo.ComboCountService;
import com.fashionapp.domain.combo.ComboItem;
import com.fashionapp.domain.combo.ComboTestSupport;
import com.fashionapp.domain.wear.WearService;
import com.fashionapp.domain.wear.WearStat;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.infra.AiServerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** FR-11 purchase check flow with the real combo engine; AI and DB are mocked. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseCheckServiceTest {

    // 2026-11-22 03:00 UTC = 12:00 KST
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-22T03:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 22);
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};

    @Mock private ClothesRepository clothesRepository;
    @Mock private ClothesClassifier clothesClassifier;
    @Mock private ClothesEmbedder clothesEmbedder;
    @Mock private ClothesEmbeddingStore embeddingStore;
    @Mock private WearService wearService;
    @Mock private AiServerClient aiServerClient;

    private final UUID userId = UUID.randomUUID();
    private final ComboCountService comboCountService = ComboTestSupport.defaultService();
    private final MockMultipartFile image = new MockMultipartFile("image", "top.jpg", "image/jpeg", JPEG);
    private PurchaseCheckService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseCheckService(clothesRepository, clothesClassifier, clothesEmbedder, embeddingStore,
                comboCountService, wearService, new PurchaseExplainer(aiServerClient),
                new PurchaseCheckRules(0.85, 5), CLOCK);
        given(clothesClassifier.classify(any(org.springframework.web.multipart.MultipartFile.class)))
                .willReturn(Optional.of(new ClothesClassification(ClothesCategory.TOP, "블랙", "블랙 hoodie")));
        given(clothesEmbedder.embedCandidate(any())).willReturn(Optional.of(new float[]{1f}));
        given(embeddingStore.findMostSimilar(eq(userId), eq(ClothesCategory.TOP), any(), anyInt()))
                .willReturn(List.of());
        given(wearService.statsFor(userId)).willReturn(Map.of());
        given(wearService.firstWornOn(userId)).willReturn(Optional.empty());
        // AI explanation down: rule sentences are used (verdict never depends on it)
        given(aiServerClient.explainPurchase(any()))
                .willThrow(new CustomException(ErrorCode.AI_SERVER_ERROR));
    }

    private static Clothes clothes(String category, String color, String name) {
        return Clothes.builder().id(UUID.randomUUID()).imageUrl("clothes/x.jpg").category(category).color(color)
                .name(name).build();
    }

    private List<Clothes> closet(int tops, int bottoms, int shoes) {
        List<Clothes> closet = new ArrayList<>();
        for (int i = 0; i < tops; i++) {
            closet.add(clothes("TOP", "화이트", "상의" + i));
        }
        for (int i = 0; i < bottoms; i++) {
            closet.add(clothes("BOTTOM", "블랙", "하의" + i));
        }
        for (int i = 0; i < shoes; i++) {
            closet.add(clothes("SHOES", "블랙", "신발" + i));
        }
        given(clothesRepository.findByUser_IdOrderByCreatedAtDesc(userId)).willReturn(closet);
        return closet;
    }

    @Test
    @DisplayName("옷장이 4벌이면 CLOSET_TOO_SMALL이고 AI를 부르지 않는다")
    void closetTooSmall() {
        closet(1, 2, 1);
        assertThatThrownBy(() -> service.check(userId, image, null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CLOSET_TOO_SMALL));
        verify(clothesClassifier, never()).classify(any(org.springframework.web.multipart.MultipartFile.class));
    }

    @Test
    @DisplayName("상의 2·하의 5·신발 1에 새 상의: +N = 5 (엔진과 같음)이고 BUY, after - before = plus")
    void buyAtFive() {
        List<Clothes> closet = closet(2, 5, 1);
        PurchaseCheckResponse result = service.check(userId, image, null);

        int enginePlus = comboCountService.additionFor(closet,
                new ComboItem(null, ClothesCategory.TOP, "블랙", null, null, null, null)).plus();
        assertThat(result.plus()).isEqualTo(enginePlus).isEqualTo(5);
        assertThat(result.verdict()).isEqualTo(PurchaseVerdict.BUY);
        assertThat(result.comboCountBefore()).isEqualTo(10);
        assertThat(result.comboCountAfter() - result.comboCountBefore()).isEqualTo(result.plus());
        assertThat(result.headline()).isNotBlank();
        assertThat(result.reasons()).isNotEmpty().allSatisfy(r -> assertThat(r).isNotBlank());
        assertThat(result.outfits()).hasSize(3)
                .allSatisfy(o -> assertThat(o.clothesIds()).hasSize(2));
    }

    @Test
    @DisplayName("상의 2·하의 2·신발 2에 새 상의: +N = 4이고 THINK")
    void thinkAtFour() {
        closet(2, 2, 2);
        PurchaseCheckResponse result = service.check(userId, image, null);
        assertThat(result.plus()).isEqualTo(4);
        assertThat(result.verdict()).isEqualTo(PurchaseVerdict.THINK);
    }

    @Test
    @DisplayName("기준값 이상 비슷한 옷이 있으면 +N과 무관하게 SIMILAR, 착용 횟수·마지막 착용일이 붙는다")
    void similar() {
        List<Clothes> closet = closet(2, 5, 1);
        Clothes top = closet.get(0);
        Clothes other = closet.get(1);
        given(embeddingStore.findMostSimilar(eq(userId), eq(ClothesCategory.TOP), any(), eq(3))).willReturn(List.of(
                new ClothesEmbeddingStore.SimilarityHit(top.getId(), 0.8499999998),
                new ClothesEmbeddingStore.SimilarityHit(other.getId(), 0.5)));
        given(wearService.statsFor(userId)).willReturn(Map.of(top.getId(),
                new WearStat(top.getId(), 2, TODAY.minusDays(5))));

        PurchaseCheckResponse result = service.check(userId, image, null);

        assertThat(result.verdict()).isEqualTo(PurchaseVerdict.SIMILAR);
        assertThat(result.similar()).extracting(PurchaseCheckResponse.Similar::id)
                .containsExactly(top.getId(), other.getId());
        assertThat(result.similar().get(0).similarity()).isEqualTo(0.85);
        assertThat(result.similar().get(0).wearCount()).isEqualTo(2);
        assertThat(result.similar().get(0).lastWornAt()).isEqualTo(TODAY.minusDays(5));
        assertThat(result.similar().get(1).wearCount()).isZero();
        assertThat(result.similar().get(1).lastWornAt()).isNull();
    }

    @Test
    @DisplayName("예상 착용: 같은 카테고리 최근 90일 착용 합 × plus ÷ 그 카테고리 코디 수 × 365 ÷ 90, 1회당 가격")
    void expectedWearsAndCostPerWear() {
        List<Clothes> closet = closet(2, 2, 1);
        Clothes a = closet.get(0);
        Clothes b = closet.get(1);
        Clothes bottom = closet.get(2);
        given(wearService.firstWornOn(userId)).willReturn(Optional.of(TODAY.minusDays(120)));
        given(wearService.statsBetween(userId, TODAY.minusDays(89), TODAY)).willReturn(Map.of(
                a.getId(), new WearStat(a.getId(), 3, TODAY.minusDays(10)),
                b.getId(), new WearStat(b.getId(), 1, TODAY.minusDays(5)),
                bottom.getId(), new WearStat(bottom.getId(), 2, TODAY.minusDays(15))));

        PurchaseCheckResponse result = service.check(userId, image, "39000");

        assertThat(result.plus()).isEqualTo(2);
        assertThat(result.expectedWears()).isEqualTo(8);
        assertThat(result.price()).isEqualTo(39000L);
        assertThat(result.costPerWear()).isEqualTo(4875L);
    }

    @Test
    @DisplayName("가격이 없으면 costPerWear null, 착용 기록이 없으면 expectedWears도 null")
    void nullsWithoutPriceOrHistory() {
        closet(2, 2, 1);
        PurchaseCheckResponse result = service.check(userId, image, null);
        assertThat(result.expectedWears()).isNull();
        assertThat(result.costPerWear()).isNull();
        assertThat(result.price()).isNull();
    }

    @Test
    @DisplayName("가격이 숫자가 아니거나 음수면 INVALID_PURCHASE_PRICE")
    void invalidPrice() {
        closet(2, 2, 1);
        for (String raw : List.of("abc", "-1", "1.5", "100000001")) {
            assertThatThrownBy(() -> service.check(userId, image, raw)).as(raw)
                    .isInstanceOfSatisfying(CustomException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_PURCHASE_PRICE));
        }
        assertThat(PurchaseCheckService.parsePrice(" ")).isNull();
        assertThat(PurchaseCheckService.parsePrice("0")).isZero();
    }

    @Test
    @DisplayName("분류·임베딩·유사도 검색이 실패하면 추측하지 않고 PURCHASE_CHECK_UNAVAILABLE")
    void unavailableWithoutComparison() {
        closet(2, 2, 1);
        given(embeddingStore.findMostSimilar(eq(userId), eq(ClothesCategory.TOP), any(), anyInt()))
                .willThrow(new DataAccessResourceFailureException("down"));
        assertUnavailable();

        given(clothesEmbedder.embedCandidate(any())).willReturn(Optional.empty());
        assertUnavailable();

        given(clothesClassifier.classify(any(org.springframework.web.multipart.MultipartFile.class)))
                .willReturn(Optional.empty());
        assertUnavailable();
    }

    private void assertUnavailable() {
        assertThatThrownBy(() -> service.check(userId, image, null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PURCHASE_CHECK_UNAVAILABLE));
    }

    @Test
    @DisplayName("옷장 분포는 미분류를 빼고 카테고리별 벌 수")
    void distribution() {
        List<Clothes> closet = new ArrayList<>(List.of(clothes("TOP", "화이트", "a"), clothes("TOP", "블랙", "b"),
                clothes("SHOES", "블랙", "c"), clothes("UNCLASSIFIED", null, null)));
        assertThat(PurchaseCheckService.distribution(closet)).isEqualTo(Map.of("TOP", 2, "SHOES", 1));
    }
}
