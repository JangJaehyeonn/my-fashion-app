package com.fashionapp.domain.clothes;

import com.fashionapp.infra.AiEmbedBatchResponse;
import com.fashionapp.infra.AiServerClient;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** FR-08: startup backfill of missing clothes embeddings (batched AI call, never blocks or fails startup). */
@ExtendWith(MockitoExtension.class)
class ClothesEmbeddingBackfillTest {

    @Mock private AiServerClient aiServerClient;
    @Mock private ClothesEmbeddingStore embeddingStore;
    @Mock private ClothesRepository clothesRepository;

    private ClothesEmbedder embedder;
    private final AtomicReference<Runnable> scheduled = new AtomicReference<>();
    private ClothesEmbeddingBackfill backfill;

    @BeforeEach
    void setUp() {
        embedder = new ClothesEmbedder(aiServerClient, embeddingStore);
        backfill = new ClothesEmbeddingBackfill(embeddingStore, clothesRepository, embedder, scheduled::set);
    }

    private static Clothes clothes(UUID id, String category, String name) {
        return Clothes.builder().id(id).imageUrl("k").category(category).color("네이비").name(name).build();
    }

    private static List<Double> vector() {
        List<Double> values = new ArrayList<>(Collections.nCopies(ClothesEmbeddingStore.DIMENSIONS, 0.0));
        values.set(0, 1.0);
        return values;
    }

    private static AiEmbedBatchResponse batch(int count) {
        AiEmbedBatchResponse response = new AiEmbedBatchResponse();
        ReflectionTestUtils.setField(response, "embeddings", new ArrayList<>(Collections.nCopies(count, vector())));
        return response;
    }

    @Test
    @DisplayName("기동 시 백필은 백그라운드로 넘기고 run은 바로 끝난다")
    void run_schedulesInBackground() {
        given(embeddingStore.isAvailable()).willReturn(true);

        backfill.run(null);

        assertThat(scheduled.get()).isNotNull();
        verifyNoInteractions(aiServerClient, clothesRepository);
    }

    @Test
    @DisplayName("pgvector가 없으면 백필을 시작하지 않는다")
    void run_skipsWithoutPgvector() {
        given(embeddingStore.isAvailable()).willReturn(false);

        backfill.run(null);

        assertThat(scheduled.get()).isNull();
    }

    @Test
    @DisplayName("임베딩이 빈 옷을 배치 한 번으로 모두 채운다")
    void backfill_fillsAllInOneBatch() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        given(embeddingStore.isAvailable()).willReturn(true);
        given(embeddingStore.findIdsWithoutEmbedding(isNull(), anyInt())).willReturn(List.of(a, b));
        given(embeddingStore.findIdsWithoutEmbedding(eq(b), anyInt())).willReturn(List.of());
        given(clothesRepository.findAllById(List.of(a, b)))
                .willReturn(List.of(clothes(a, "TOP", "니트"), clothes(b, "SHOES", "스니커즈")));
        given(aiServerClient.embedBatch(List.of("상의 네이비 니트", "신발 네이비 스니커즈"))).willReturn(batch(2));
        given(embeddingStore.save(any(), any())).willReturn(true);

        assertThat(backfill.backfill()).isEqualTo(2);

        verify(aiServerClient, times(1)).embedBatch(anyList());
        verify(aiServerClient, never()).embed(any());
        verify(embeddingStore).save(eq(a), any());
        verify(embeddingStore).save(eq(b), any());
    }

    @Test
    @DisplayName("다음 페이지는 마지막 id 뒤에서 이어 읽는다")
    void backfill_pagesByLastId() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        given(embeddingStore.isAvailable()).willReturn(true);
        given(embeddingStore.findIdsWithoutEmbedding(isNull(), anyInt())).willReturn(List.of(a));
        given(embeddingStore.findIdsWithoutEmbedding(eq(a), anyInt())).willReturn(List.of(b));
        given(embeddingStore.findIdsWithoutEmbedding(eq(b), anyInt())).willReturn(List.of());
        given(clothesRepository.findAllById(List.of(a))).willReturn(List.of(clothes(a, "TOP", "니트")));
        given(clothesRepository.findAllById(List.of(b))).willReturn(List.of(clothes(b, "BOTTOM", "슬랙스")));
        given(aiServerClient.embedBatch(anyList())).willReturn(batch(1));
        given(embeddingStore.save(any(), any())).willReturn(true);

        assertThat(backfill.backfill()).isEqualTo(2);
    }

    @Test
    @DisplayName("임베딩 API가 다운이면 아무것도 저장하지 않고 멈춘다 (예외 없음)")
    void backfill_stopsWhenEmbeddingDown() {
        UUID a = UUID.randomUUID();
        given(embeddingStore.isAvailable()).willReturn(true);
        given(embeddingStore.findIdsWithoutEmbedding(isNull(), anyInt())).willReturn(List.of(a));
        given(clothesRepository.findAllById(List.of(a))).willReturn(List.of(clothes(a, "TOP", "니트")));
        willThrow(new CustomException(ErrorCode.AI_SERVER_ERROR)).given(aiServerClient).embedBatch(anyList());

        assertThat(backfill.backfill()).isZero();

        verify(embeddingStore, never()).save(any(), any());
        verify(embeddingStore, times(1)).findIdsWithoutEmbedding(any(), anyInt());
    }

    @Test
    @DisplayName("백그라운드 작업이 예외를 던져도 밖으로 새지 않는다")
    void backgroundTask_swallowsFailures() {
        given(embeddingStore.isAvailable()).willReturn(true);
        given(embeddingStore.findIdsWithoutEmbedding(any(), anyInt())).willThrow(new IllegalStateException("db"));
        backfill.run(null);

        scheduled.get().run();
    }

    @Test
    @DisplayName("응답 개수가 요청과 다르면 잘못된 응답으로 보고 저장하지 않는다")
    void fillBatch_rejectsMismatchedResponse() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        given(embeddingStore.isAvailable()).willReturn(true);
        given(aiServerClient.embedBatch(anyList())).willReturn(batch(1));

        ClothesEmbedder.BatchResult result = embedder.fillBatch(
                List.of(clothes(a, "TOP", "니트"), clothes(b, "TOP", "셔츠")));

        assertThat(result.failed()).isTrue();
        verify(embeddingStore, never()).save(any(), any());
    }

    @Test
    @DisplayName("미분류 옷은 배치에서 빠지고 AI를 부르지 않는다")
    void fillBatch_skipsUnclassified() {
        ClothesEmbedder.BatchResult result = embedder.fillBatch(
                List.of(clothes(UUID.randomUUID(), "UNCLASSIFIED", "사진")));

        assertThat(result.attempted()).isZero();
        assertThat(result.failed()).isFalse();
        verifyNoInteractions(aiServerClient);
    }

    @Test
    @DisplayName("일부 벡터가 잘못되면 나머지만 저장한다")
    void fillBatch_savesValidVectorsOnly() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        AiEmbedBatchResponse response = new AiEmbedBatchResponse();
        ReflectionTestUtils.setField(response, "embeddings", List.of(vector(), List.of(1.0, 2.0)));
        given(embeddingStore.isAvailable()).willReturn(true);
        given(aiServerClient.embedBatch(anyList())).willReturn(response);
        given(embeddingStore.save(eq(a), any())).willReturn(true);

        ClothesEmbedder.BatchResult result = embedder.fillBatch(
                List.of(clothes(a, "TOP", "니트"), clothes(b, "TOP", "셔츠")));

        assertThat(result.saved()).isEqualTo(1);
        verify(embeddingStore, never()).save(eq(b), any());
    }
}
