package com.fashionapp.domain.combo;

import com.fashionapp.domain.clothes.Clothes;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Per-user cache in front of {@link ComboEngine}. The cached result is keyed by the user and remembers the closet
 * snapshot (the combo-relevant attributes of every piece) it was computed from, so any add, delete or edit — through
 * any code path — makes the next lookup recompute, and an unchanged closet reuses the result.
 */
@Service
public class ComboCountService {

    private static final Comparator<ComboItem> SNAPSHOT_ORDER =
            Comparator.comparing(ComboItem::id, Comparator.nullsLast(Comparator.naturalOrder()));

    private record Cached(List<ComboItem> snapshot, ComboStats stats) {
    }

    private final ComboEngine engine;
    private final Cache<UUID, Cached> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofHours(1))
            .build();

    public ComboCountService(ComboEngine engine) {
        this.engine = engine;
    }

    /** Combo counts of the user's closet given its current clothes (recomputed only when the closet changed). */
    public ComboStats statsFor(UUID userId, List<Clothes> closet) {
        List<ComboItem> snapshot = snapshotOf(closet);
        Cached cached = cache.getIfPresent(userId);
        if (cached != null && cached.snapshot().equals(snapshot)) {
            return cached.stats();
        }
        ComboStats stats = engine.count(snapshot);
        cache.put(userId, new Cached(snapshot, stats));
        return stats;
    }

    /** +N and the new valid combos when a hypothetical piece (attributes only) joins the closet. */
    public ComboAddition additionFor(List<Clothes> closet, ComboItem candidate) {
        return engine.addition(snapshotOf(closet), candidate);
    }

    /** Drops the cached result, e.g. when the account is deleted. */
    public void evict(UUID userId) {
        cache.invalidate(userId);
    }

    private static List<ComboItem> snapshotOf(List<Clothes> closet) {
        return closet.stream().map(ComboItem::of).sorted(SNAPSHOT_ORDER).toList();
    }
}
