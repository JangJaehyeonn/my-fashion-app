package com.fashionapp.domain.purchase;

import com.fashionapp.infra.AiPurchaseExplainRequest;
import com.fashionapp.infra.AiPurchaseExplainResponse;
import com.fashionapp.infra.AiServerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * FR-11: asks the AI server for up to 3 representative combos and the sentences, then validates the answer.
 * Picks outside the candidate list are dropped; missing or malformed sentences — or an AI that reports another
 * verdict — fall back to {@link PurchaseFallbackText}. Never throws and never changes the rule verdict.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseExplainer {

    static final int MAX_OUTFITS = 3;
    static final int REASON_COUNT = 3;
    /** Candidates sent to the AI at most (the engine order is deterministic, so the same ones every time). */
    static final int MAX_CANDIDATES = 30;
    static final int MAX_LABEL_LENGTH = 20;
    static final int MAX_SENTENCE_LENGTH = 200;

    /** Sentences and outfits for the response; notes are keyed by similar clothes id. */
    public record Explanation(List<PurchaseCheckResponse.Outfit> outfits, String headline, List<String> reasons,
                              String gap, Map<UUID, String> notes) {
    }

    private final AiServerClient aiServerClient;

    public Explanation explain(PurchaseContext context) {
        List<List<UUID>> candidates = context.candidates().stream().limit(MAX_CANDIDATES).toList();
        AiPurchaseExplainResponse response = call(context, candidates);
        List<PurchaseCheckResponse.Outfit> outfits = validPicks(response, candidates);
        if (outfits.isEmpty()) {
            outfits = candidates.stream().limit(MAX_OUTFITS)
                    .map(ids -> new PurchaseCheckResponse.Outfit(ids, PurchaseFallbackText.DEFAULT_LABEL))
                    .toList();
        }
        if (!hasValidSentences(response, context.verdict())) {
            return new Explanation(outfits, PurchaseFallbackText.headline(context),
                    PurchaseFallbackText.reasons(context), null, Map.of());
        }
        List<String> reasons = response.getReasons().stream().map(String::trim).limit(REASON_COUNT).toList();
        return new Explanation(outfits, response.getHeadline().trim(), reasons, sentenceOrNull(response.getGap()),
                notes(response, context.similar()));
    }

    private AiPurchaseExplainResponse call(PurchaseContext context, List<List<UUID>> candidates) {
        try {
            return aiServerClient.explainPurchase(toRequest(context, candidates));
        } catch (RuntimeException e) {
            // AiServerClient already logs the cause (timeout / server error)
            log.warn("AI purchase explain unavailable, using rule sentences: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    static AiPurchaseExplainRequest toRequest(PurchaseContext context, List<List<UUID>> candidates) {
        PurchaseCheckResponse.Item item = context.item();
        AiPurchaseExplainRequest.Item aiItem = new AiPurchaseExplainRequest.Item(item.category().name(),
                item.color(), item.name(), item.fit(), item.material(), item.pattern(), item.season());
        List<AiPurchaseExplainRequest.Similar> similar = context.similar().stream()
                .map(s -> new AiPurchaseExplainRequest.Similar(s.id().toString(), s.name(), s.similarity(),
                        s.wearCount(), s.lastWornAt() == null ? null : s.lastWornAt().toString()))
                .toList();
        List<AiPurchaseExplainRequest.Candidate> aiCandidates = candidates.stream()
                .map(ids -> new AiPurchaseExplainRequest.Candidate(ids.stream().map(UUID::toString).toList(),
                        ids.stream().map(id -> context.pieceLabels().getOrDefault(id, "")).toList()))
                .toList();
        return new AiPurchaseExplainRequest(aiItem, context.verdict().name(), context.plus(),
                context.comboCountBefore(), context.comboCountAfter(), similar, context.distribution(), aiCandidates);
    }

    /** AI picks that are exactly one of the candidates (as a set of ids), deduplicated, at most 3, AI order. */
    static List<PurchaseCheckResponse.Outfit> validPicks(AiPurchaseExplainResponse response,
                                                         List<List<UUID>> candidates) {
        if (response == null || response.getPicks() == null) {
            return List.of();
        }
        Map<Set<String>, List<UUID>> bySet = new HashMap<>();
        for (List<UUID> ids : candidates) {
            Set<String> key = new HashSet<>();
            ids.forEach(id -> key.add(id.toString()));
            bySet.putIfAbsent(key, ids);
        }
        Map<List<UUID>, String> picked = new LinkedHashMap<>();
        for (AiPurchaseExplainResponse.Pick pick : response.getPicks()) {
            if (pick == null || pick.getClothesIds() == null || picked.size() == MAX_OUTFITS) {
                continue;
            }
            List<UUID> match = bySet.get(new HashSet<>(pick.getClothesIds()));
            if (match != null) {
                picked.putIfAbsent(match, label(pick.getLabel()));
            }
        }
        List<PurchaseCheckResponse.Outfit> outfits = new ArrayList<>();
        picked.forEach((ids, label) -> outfits.add(new PurchaseCheckResponse.Outfit(ids, label)));
        return outfits;
    }

    static boolean hasValidSentences(AiPurchaseExplainResponse response, PurchaseVerdict verdict) {
        if (response == null || sentenceOrNull(response.getHeadline()) == null || response.getReasons() == null) {
            return false;
        }
        if (response.getVerdict() != null && !response.getVerdict().trim().equalsIgnoreCase(verdict.name())) {
            log.warn("AI purchase explain disagreed with the rule verdict, using rule sentences");
            return false;
        }
        return response.getReasons().size() >= REASON_COUNT
                && response.getReasons().stream().limit(REASON_COUNT).allMatch(r -> sentenceOrNull(r) != null);
    }

    private static Map<UUID, String> notes(AiPurchaseExplainResponse response,
                                           List<PurchaseCheckResponse.Similar> similar) {
        if (response.getSimilarNotes() == null) {
            return Map.of();
        }
        Map<String, UUID> ids = new HashMap<>();
        similar.forEach(s -> ids.put(s.id().toString(), s.id()));
        Map<UUID, String> notes = new HashMap<>();
        response.getSimilarNotes().stream().filter(Objects::nonNull).forEach(n -> {
            UUID id = n.getId() == null ? null : ids.get(n.getId().trim());
            String note = sentenceOrNull(n.getNote());
            if (id != null && note != null) {
                notes.putIfAbsent(id, note);
            }
        });
        return notes;
    }

    private static String label(String raw) {
        if (raw == null || raw.isBlank() || raw.trim().length() > MAX_LABEL_LENGTH) {
            return PurchaseFallbackText.DEFAULT_LABEL;
        }
        return raw.trim();
    }

    private static String sentenceOrNull(String raw) {
        if (raw == null || raw.isBlank() || raw.trim().length() > MAX_SENTENCE_LENGTH) {
            return null;
        }
        return raw.trim();
    }
}
