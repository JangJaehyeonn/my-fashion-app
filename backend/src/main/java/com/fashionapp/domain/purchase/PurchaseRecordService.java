package com.fashionapp.domain.purchase;

import com.fashionapp.domain.clothes.Clothes;
import com.fashionapp.domain.clothes.ClothesCategory;
import com.fashionapp.domain.clothes.ClothesClassification;
import com.fashionapp.domain.clothes.ClothesRepository;
import com.fashionapp.domain.clothes.ClothesResponse;
import com.fashionapp.domain.clothes.ClothesSeason;
import com.fashionapp.domain.clothes.ClothesService;
import com.fashionapp.domain.event.EventRecorder;
import com.fashionapp.domain.event.EventType;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.infra.ImageKeys;
import com.fashionapp.infra.ImageStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * FR-12 purchase check records: the temporary photo ({@code tmp/{userId}/{checkId}.ext}, 30 minutes), the record in
 * purchase_checks, BUY/SKIP decisions and the history. Someone else's check is always 404.
 *
 * <p>No transaction spans storage calls (same reason as ClothesService.register): each repository call is short.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseRecordService {

    static final int HISTORY_LIMIT = 20;

    private final PurchaseCheckRepository repository;
    private final PurchaseDraftStore draftStore;
    private final ImageStorage imageStorage;
    private final ClothesService clothesService;
    private final ClothesRepository clothesRepository;
    private final Clock clock;
    private final EventRecorder eventRecorder;

    /** Stores the photo under tmp/ and records an UNDECIDED check. Returns the check id. */
    public UUID open(UUID userId, byte[] photo, String contentType, PurchaseCheckResponse.Item item,
                     PurchaseVerdict verdict, int plus, Long price, float[] embedding) {
        UUID checkId = UUID.randomUUID();
        String tempKey = imageStorage.storeBytesAt(ImageKeys.tempKey(userId, checkId, contentType), photo, contentType);
        try {
            repository.save(new PurchaseCheck(checkId, userId, item, verdict, plus, price, tempKey, clock.instant()));
        } catch (RuntimeException e) {
            deletePhoto(tempKey);
            throw e;
        }
        draftStore.save(checkId, draftOf(item, embedding));
        return checkId;
    }

    public PurchaseRecordResponse get(UUID userId, UUID checkId) {
        PurchaseCheck check = findOwned(userId, checkId);
        return toResponses(userId, List.of(check)).get(0);
    }

    /** The latest {@link #HISTORY_LIMIT} checks, newest first. */
    public List<PurchaseRecordResponse> history(UUID userId) {
        return toResponses(userId,
                repository.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, HISTORY_LIMIT)));
    }

    /** FR-13: an undecided check whose photo is still there, with its analysed attributes and price. */
    public record Editable(ClothesClassification current, Long price) {
    }

    /** FR-13: someone else's = 404, decided = 409, expired = 410 (the photo and so BUY are gone). */
    public Editable editable(UUID userId, UUID checkId) {
        PurchaseCheck check = findOwned(userId, checkId);
        if (check.decisionValue() != PurchaseDecision.UNDECIDED) {
            throw new CustomException(ErrorCode.PURCHASE_ALREADY_DECIDED);
        }
        if (check.isExpired(clock.instant())) {
            throw new CustomException(ErrorCode.PURCHASE_CHECK_EXPIRED);
        }
        return new Editable(classificationOf(check, draftStore.find(checkId).orElse(null)), check.getPrice());
    }

    /**
     * FR-13: stores a re-judgement on the same record and draft (BUY then registers the corrected piece). 409 when a
     * decision was made while re-judging.
     */
    public void reanalyzed(UUID checkId, PurchaseCheckResponse.Item item, PurchaseVerdict verdict, int plus,
                           float[] embedding) {
        if (repository.updateUndecided(checkId, item.name(), item.category().name(), item.color(), verdict.name(),
                plus) == 0) {
            throw new CustomException(ErrorCode.PURCHASE_ALREADY_DECIDED);
        }
        draftStore.save(checkId, draftOf(item, embedding));
    }

    private static PurchaseDraftStore.Draft draftOf(PurchaseCheckResponse.Item item, float[] embedding) {
        return new PurchaseDraftStore.Draft(item.fit(), item.material(), item.pattern(), item.season(), embedding);
    }

    public PurchaseDecisionResponse decide(UUID userId, UUID checkId, PurchaseDecisionRequest.Choice choice) {
        PurchaseCheck check = findOwned(userId, checkId);
        if (check.decisionValue() != PurchaseDecision.UNDECIDED) {
            throw new CustomException(ErrorCode.PURCHASE_ALREADY_DECIDED);
        }
        if (check.isExpired(clock.instant())) {
            throw new CustomException(ErrorCode.PURCHASE_CHECK_EXPIRED);
        }
        PurchaseDecisionResponse response = choice == PurchaseDecisionRequest.Choice.BUY
                ? buy(userId, check) : skip(userId, check);
        eventRecorder.record(userId, EventType.PURCHASE_DECIDED,
                Map.of("decision", choice.name(), "verdict", String.valueOf(check.getVerdict())));
        return response;
    }

    private PurchaseDecisionResponse skip(UUID userId, PurchaseCheck check) {
        claim(check, PurchaseDecision.SKIPPED);
        check.markSkipped();
        finish(check);
        return new PurchaseDecisionResponse(get(userId, check.getId()), null);
    }

    private PurchaseDecisionResponse buy(UUID userId, PurchaseCheck check) {
        String tempKey = check.getTempPhotoKey();
        // The scheduler may have removed the photo just now: then it is expired like any other
        byte[] photo = imageStorage.readBytes(tempKey)
                .orElseThrow(() -> new CustomException(ErrorCode.PURCHASE_CHECK_EXPIRED));
        claim(check, PurchaseDecision.BOUGHT);
        ClothesResponse clothes;
        try {
            String imageKey = imageStorage.storeBytes(photo, ImageKeys.contentTypeOf(tempKey), "clothes", userId);
            PurchaseDraftStore.Draft draft = draftStore.find(check.getId()).orElse(null);
            clothes = clothesService.registerPurchased(userId, imageKey, classificationOf(check, draft),
                    draft == null ? null : draft.embedding());
        } catch (RuntimeException e) {
            repository.releaseClaim(check.getId(), PurchaseDecision.BOUGHT.name());
            throw e;
        }
        check.markBought(clothes.getId());
        finish(check);
        return new PurchaseDecisionResponse(get(userId, check.getId()), clothes);
    }

    /** Removes the temporary photo and draft; a photo that could not be deleted stays recorded for the scheduler. */
    private void finish(PurchaseCheck check) {
        if (deletePhoto(check.getTempPhotoKey())) {
            check.photoRemoved();
        }
        repository.save(check);
        draftStore.delete(List.of(check.getId()));
    }

    /** Only one decision can win; a concurrent second one sees it as already decided. */
    private void claim(PurchaseCheck check, PurchaseDecision decision) {
        if (repository.claimUndecided(check.getId(), decision.name()) == 0) {
            throw new CustomException(ErrorCode.PURCHASE_ALREADY_DECIDED);
        }
    }

    static ClothesClassification classificationOf(PurchaseCheck check, PurchaseDraftStore.Draft draft) {
        ClothesCategory category = ClothesCategory.from(check.getCategory());
        if (draft == null) {
            return new ClothesClassification(category, check.getColor(), check.getName());
        }
        return new ClothesClassification(category, check.getColor(), check.getName(), draft.fit(),
                draft.material(), draft.pattern(), ClothesSeason.parse(draft.season()).orElse(null));
    }

    private PurchaseCheck findOwned(UUID userId, UUID checkId) {
        return repository.findByIdAndUserId(checkId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.PURCHASE_CHECK_NOT_FOUND));
    }

    private List<PurchaseRecordResponse> toResponses(UUID userId, List<PurchaseCheck> checks) {
        Set<UUID> clothesIds = checks.stream().map(PurchaseCheck::getClothesId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Clothes> clothes = clothesIds.isEmpty() ? Map.of()
                : clothesRepository.findByUser_IdAndIdIn(userId, clothesIds).stream()
                        .collect(Collectors.toMap(Clothes::getId, Function.identity()));
        return checks.stream()
                .map(check -> toResponse(check,
                        check.getClothesId() == null ? null : clothes.get(check.getClothesId())))
                .toList();
    }

    private PurchaseRecordResponse toResponse(PurchaseCheck check, Clothes clothes) {
        PurchaseDecision decision = check.decisionValue();
        // Only BUY keeps a photo (as the clothes photo); if that piece was deleted later there is none
        String imageUrl = decision == PurchaseDecision.BOUGHT && clothes != null
                ? imageStorage.resolveUrl(clothes.getImageUrl()) : null;
        return new PurchaseRecordResponse(check.getId(), check.getName(), ClothesCategory.from(check.getCategory()),
                check.getColor(), check.getPrice(), PurchaseVerdict.valueOf(check.getVerdict()), check.getPlus(),
                decision, check.getClothesId(), imageUrl, check.getCreatedAt());
    }

    /** True when the photo is gone (deleted now or never there). */
    private boolean deletePhoto(String tempKey) {
        if (tempKey == null) {
            return true;
        }
        try {
            imageStorage.delete(tempKey);
            return true;
        } catch (RuntimeException e) {
            log.warn("purchase temp photo delete failed: {}", e.getClass().getSimpleName());
            return false;
        }
    }
}
