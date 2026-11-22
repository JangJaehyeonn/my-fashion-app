package com.fashionapp.domain.purchase;

import com.fashionapp.domain.clothes.Clothes;
import com.fashionapp.domain.clothes.ClothesCategory;
import com.fashionapp.domain.clothes.ClothesClassification;
import com.fashionapp.domain.clothes.ClothesClassifier;
import com.fashionapp.domain.clothes.ClothesEmbedder;
import com.fashionapp.domain.clothes.ClothesEmbeddingStore;
import com.fashionapp.domain.clothes.ClothesRepository;
import com.fashionapp.domain.combo.Combo;
import com.fashionapp.domain.combo.ComboAddition;
import com.fashionapp.domain.combo.ComboCountService;
import com.fashionapp.domain.combo.ComboItem;
import com.fashionapp.domain.combo.ComboStats;
import com.fashionapp.domain.wear.WearCalendar;
import com.fashionapp.domain.wear.WearService;
import com.fashionapp.domain.wear.WearStat;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.infra.ImageKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * FR-11 purchase check: classify → embed → top 3 similar pieces of the same category → +N with the combo engine →
 * rule verdict → AI picks and sentences (validated, rule fallback). The closet is unchanged; FR-12 keeps the photo
 * for 30 minutes and records the check (PurchaseRecordService), at most 30 checks a day (PurchaseDailyLimiter).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseCheckService {

    static final int MIN_CLOSET_SIZE = 5;
    static final int SIMILAR_LIMIT = 3;
    static final long MAX_PRICE = 100_000_000L;
    /** Same as storage.max-bytes and the multipart limit (10MB). */
    static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    private final ClothesRepository clothesRepository;
    private final ClothesClassifier clothesClassifier;
    private final ClothesEmbedder clothesEmbedder;
    private final ClothesEmbeddingStore embeddingStore;
    private final ComboCountService comboCountService;
    private final WearService wearService;
    private final PurchaseExplainer explainer;
    private final PurchaseCheckRules rules;
    private final PurchaseDailyLimiter dailyLimiter;
    private final PurchaseRecordService recordService;
    private final Clock clock;

    // AI 호출(분류·임베딩·이유 문장)을 기다리는 동안 DB 커넥션을 붙잡지 않도록 트랜잭션을 걸지 않음 (ClothesService.register와 같은 이유)
    public PurchaseCheckResponse check(UUID userId, MultipartFile image, String rawPrice) {
        Long price = parsePrice(rawPrice);
        byte[] photo = ImageKeys.validate(image, MAX_IMAGE_BYTES);
        long t0 = System.nanoTime();
        List<Clothes> closet = clothesRepository.findByUser_IdOrderByCreatedAtDesc(userId);
        if (closet.size() < MIN_CLOSET_SIZE) {
            throw new CustomException(ErrorCode.CLOSET_TOO_SMALL);
        }
        dailyLimiter.acquire(userId);
        ClothesClassification item = clothesClassifier.classify(image)
                .orElseThrow(() -> new CustomException(ErrorCode.PURCHASE_CHECK_UNAVAILABLE));
        long t1 = System.nanoTime();
        float[] vector = clothesEmbedder.embedCandidate(item)
                .orElseThrow(() -> new CustomException(ErrorCode.PURCHASE_CHECK_UNAVAILABLE));
        long t2 = System.nanoTime();
        List<ClothesEmbeddingStore.SimilarityHit> hits = findSimilar(userId, item.category(), vector);
        long t3 = System.nanoTime();

        ComboAddition addition = comboCountService.additionFor(closet, toComboItem(item));
        ComboStats stats = comboCountService.statsFor(userId, closet);
        long t4 = System.nanoTime();

        Map<UUID, Clothes> byId = closet.stream().collect(Collectors.toMap(Clothes::getId, Function.identity()));
        List<PurchaseCheckResponse.Similar> similar = toSimilar(hits, byId, wearService.statsFor(userId));
        PurchaseVerdict verdict = rules.verdict(topSimilarity(similar), addition.plus());
        Integer expectedWears = expectedWears(userId, closet, stats, item.category(), addition.plus());
        long t5 = System.nanoTime();

        PurchaseContext context = new PurchaseContext(toItem(item), verdict, addition.plus(), addition.before(),
                addition.after(), similar, distribution(closet), candidates(addition),
                (int) closet.stream().filter(c -> categoryOf(c) == item.category()).count(), pieceLabels(closet));
        PurchaseExplainer.Explanation explanation = explainer.explain(context);
        long t6 = System.nanoTime();

        // 판정이 느릴 때 어느 구간인지 가려내기 위한 구간별 소요 시간 (개인정보 없음)
        log.info("purchase.check closet={} verdict={} plus={} classify={}ms embed={}ms similar={}ms combo={}ms "
                        + "rules={}ms explain={}ms",
                closet.size(), verdict, addition.plus(), ms(t0, t1), ms(t1, t2), ms(t2, t3), ms(t3, t4),
                ms(t4, t5), ms(t5, t6));
        UUID checkId = recordService.open(userId, photo, image.getContentType(), context.item(), verdict,
                addition.plus(), price, vector);
        return new PurchaseCheckResponse(checkId, context.item(), verdict, addition.plus(),
                addition.before(), addition.after(), withNotes(similar, explanation.notes()), explanation.outfits(),
                explanation.headline(), explanation.reasons(), explanation.gap(), expectedWears, price,
                ExpectedWears.costPerWear(price, expectedWears));
    }

    /** Optional price in won: blank = none, otherwise a whole number 0..MAX_PRICE. */
    static Long parsePrice(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            long price = Long.parseLong(raw.trim());
            if (price < 0 || price > MAX_PRICE) {
                throw new CustomException(ErrorCode.INVALID_PURCHASE_PRICE);
            }
            return price;
        } catch (NumberFormatException e) {
            throw new CustomException(ErrorCode.INVALID_PURCHASE_PRICE);
        }
    }

    private List<ClothesEmbeddingStore.SimilarityHit> findSimilar(UUID userId, ClothesCategory category,
                                                                   float[] vector) {
        try {
            return embeddingStore.findMostSimilar(userId, category, vector, SIMILAR_LIMIT);
        } catch (RuntimeException e) {
            // Without the comparison a SIMILAR verdict could be missed, so the check is refused instead of guessed
            log.warn("purchase similarity search failed: {}", e.getClass().getSimpleName());
            throw new CustomException(ErrorCode.PURCHASE_CHECK_UNAVAILABLE);
        }
    }

    static List<PurchaseCheckResponse.Similar> toSimilar(List<ClothesEmbeddingStore.SimilarityHit> hits,
                                                         Map<UUID, Clothes> closet, Map<UUID, WearStat> wears) {
        return hits.stream()
                .filter(hit -> closet.containsKey(hit.clothesId()))
                .map(hit -> {
                    WearStat wear = wears.get(hit.clothesId());
                    return new PurchaseCheckResponse.Similar(hit.clothesId(), closet.get(hit.clothesId()).getName(),
                            round4(hit.similarity()), wear == null ? 0 : (int) wear.count(),
                            wear == null ? null : wear.lastWornOn(), null);
                })
                .toList();
    }

    private static OptionalDouble topSimilarity(List<PurchaseCheckResponse.Similar> similar) {
        return similar.stream().mapToDouble(PurchaseCheckResponse.Similar::similarity).max();
    }

    private Integer expectedWears(UUID userId, List<Clothes> closet, ComboStats stats, ClothesCategory category,
                                  int plus) {
        LocalDate today = WearCalendar.today(clock);
        LocalDate firstWornOn = wearService.firstWornOn(userId).orElse(null);
        if (firstWornOn == null) {
            return null;
        }
        Map<UUID, WearStat> recent = wearService.statsBetween(userId, ExpectedWears.windowStart(today), today);
        List<Clothes> sameCategory = closet.stream().filter(c -> categoryOf(c) == category).toList();
        long recentWears = sameCategory.stream().map(c -> recent.get(c.getId())).filter(Objects::nonNull)
                .mapToLong(WearStat::count).sum();
        // A combo holds at most one piece per category, so this sum is the number of combos with that category
        int categoryCombos = sameCategory.stream().mapToInt(c -> stats.countOf(c.getId())).sum();
        return ExpectedWears.expectedWears(recentWears, plus, categoryCombos, firstWornOn, today);
    }

    /** Combos with the new piece as closet ids only, in the engine's deterministic order. */
    static List<List<UUID>> candidates(ComboAddition addition) {
        return addition.combos().stream().map(PurchaseCheckService::closetIds).toList();
    }

    private static List<UUID> closetIds(Combo combo) {
        return combo.items().stream().map(ComboItem::id).filter(Objects::nonNull).toList();
    }

    private static Map<UUID, String> pieceLabels(List<Clothes> closet) {
        return closet.stream().collect(Collectors.toMap(Clothes::getId, c -> Stream.of(
                        PurchaseFallbackText.categoryLabel(categoryOf(c)), c.getColor(), c.getName())
                .filter(Objects::nonNull).collect(Collectors.joining(" "))));
    }

    static Map<String, Integer> distribution(List<Clothes> closet) {
        Map<String, Integer> counts = new TreeMap<>();
        closet.stream().map(PurchaseCheckService::categoryOf)
                .filter(c -> c != ClothesCategory.UNCLASSIFIED)
                .forEach(c -> counts.merge(c.name(), 1, Integer::sum));
        return counts;
    }

    private static List<PurchaseCheckResponse.Similar> withNotes(List<PurchaseCheckResponse.Similar> similar,
                                                                 Map<UUID, String> notes) {
        return similar.stream()
                .map(s -> new PurchaseCheckResponse.Similar(s.id(), s.name(), s.similarity(), s.wearCount(),
                        s.lastWornAt(), notes.get(s.id())))
                .toList();
    }

    private static ComboItem toComboItem(ClothesClassification item) {
        return new ComboItem(null, item.category(), item.color(),
                item.season() == null ? null : item.season().name(), item.pattern(), item.fit(), item.material());
    }

    private static PurchaseCheckResponse.Item toItem(ClothesClassification item) {
        return new PurchaseCheckResponse.Item(item.category(), item.color(), item.name(), item.fit(),
                item.material(), item.pattern(), item.season() == null ? null : item.season().name());
    }

    private static ClothesCategory categoryOf(Clothes clothes) {
        return ClothesCategory.from(clothes.getCategory());
    }

    private static double round4(double value) {
        return Math.round(value * 10_000) / 10_000.0;
    }

    private static long ms(long from, long to) {
        return (to - from) / 1_000_000;
    }
}
