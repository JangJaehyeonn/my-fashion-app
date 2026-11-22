package com.fashionapp.domain.purchase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * FR-12: the analysed attributes and embedding of a purchase check, kept in Redis ({@code purchase:draft:{checkId}})
 * so BUY can create the clothes exactly as analysed without classifying again. Lives a little longer than the photo;
 * the check record decides expiry. Best-effort: a missing draft only means BUY re-embeds (never re-classifies).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseDraftStore {

    static final Duration TTL = PurchaseCheck.PHOTO_TTL.plusMinutes(10);

    /** Attributes not in the check record (null = unknown) and the embedding (null = none). */
    public record Draft(String fit, String material, String pattern, String season, float[] embedding) {
    }

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public void save(UUID checkId, Draft draft) {
        try {
            redisTemplate.opsForValue().set(key(checkId), objectMapper.writeValueAsString(draft), TTL);
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("purchase draft save failed: {}", e.getClass().getSimpleName());
        }
    }

    public Optional<Draft> find(UUID checkId) {
        try {
            String json = redisTemplate.opsForValue().get(key(checkId));
            return json == null ? Optional.empty() : Optional.of(objectMapper.readValue(json, Draft.class));
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("purchase draft read failed: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public void delete(Collection<UUID> checkIds) {
        if (checkIds.isEmpty()) {
            return;
        }
        try {
            redisTemplate.delete(checkIds.stream().map(PurchaseDraftStore::key).toList());
        } catch (RuntimeException e) {
            log.warn("purchase draft delete failed: {}", e.getClass().getSimpleName());
        }
    }

    static String key(UUID checkId) {
        return "purchase:draft:" + checkId;
    }
}
