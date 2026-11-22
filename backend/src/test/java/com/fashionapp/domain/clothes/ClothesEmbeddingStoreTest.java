package com.fashionapp.domain.clothes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** FR-07: pgvector setup and the vector literal format. */
class ClothesEmbeddingStoreTest {

    @Test
    @DisplayName("기동 시 확장과 embedding 컬럼을 멱등 SQL로 만든다")
    void run_createsExtensionAndColumn() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ClothesEmbeddingStore store = new ClothesEmbeddingStore(jdbc);

        store.run(null);

        verify(jdbc).execute("CREATE EXTENSION IF NOT EXISTS vector");
        verify(jdbc).execute("ALTER TABLE clothes ADD COLUMN IF NOT EXISTS embedding vector(1536)");
        assertThat(store.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("pgvector가 없는 DB면 임베딩만 끄고 기동은 계속한다")
    void run_withoutPgvector_disables() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        willThrow(new DataAccessResourceFailureException("extension \"vector\" is not available"))
                .given(jdbc).execute("CREATE EXTENSION IF NOT EXISTS vector");
        ClothesEmbeddingStore store = new ClothesEmbeddingStore(jdbc);

        store.run(null);

        assertThat(store.isAvailable()).isFalse();
        assertThat(store.save(UUID.randomUUID(), new float[]{1f})).isFalse();
        store.clear(UUID.randomUUID());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("벡터는 pgvector 텍스트 형식 [v1,v2,...]으로 보낸다")
    void toLiteral_format() {
        assertThat(ClothesEmbeddingStore.toLiteral(new float[]{0.5f, -1f, 0f})).isEqualTo("[0.5,-1.0,0.0]");
    }
}
