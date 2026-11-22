package com.fashionapp.domain.clothes;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Reads and writes clothes.embedding (pgvector vector(1536)) with native SQL (FR-07, ADR 0006).
 *
 * <p>On startup it enables the pgvector extension and adds the column (both idempotent, same as
 * db/phase10_clothes_embedding.sql). If the database has no pgvector (e.g. a plain postgres image), embeddings are
 * disabled and clothes still work without them.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClothesEmbeddingStore implements ApplicationRunner {

    public static final int DIMENSIONS = 1536;

    private final JdbcTemplate jdbcTemplate;
    private volatile boolean available;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
            jdbcTemplate.execute("ALTER TABLE clothes ADD COLUMN IF NOT EXISTS embedding vector(" + DIMENSIONS + ")");
            available = true;
        } catch (DataAccessException e) {
            available = false;
            log.warn("pgvector unavailable, clothes embeddings disabled: {}", e.getMostSpecificCause().getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /** Stores a vector of exactly DIMENSIONS values. Returns false when it could not be written. */
    public boolean save(UUID clothesId, float[] vector) {
        if (!available) {
            return false;
        }
        try {
            jdbcTemplate.update("UPDATE clothes SET embedding = CAST(? AS vector) WHERE id = ?",
                    toLiteral(vector), clothesId);
            return true;
        } catch (DataAccessException e) {
            log.warn("clothes embedding save failed for {}: {}", clothesId, e.getMostSpecificCause().getMessage());
            return false;
        }
    }

    /** Removes a stale embedding (the text it was computed from has changed). */
    public void clear(UUID clothesId) {
        if (!available) {
            return;
        }
        try {
            jdbcTemplate.update("UPDATE clothes SET embedding = NULL WHERE id = ?", clothesId);
        } catch (DataAccessException e) {
            log.warn("clothes embedding clear failed for {}: {}", clothesId, e.getMostSpecificCause().getMessage());
        }
    }

    /** pgvector text input format: [v1,v2,...]. */
    static String toLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 12).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
