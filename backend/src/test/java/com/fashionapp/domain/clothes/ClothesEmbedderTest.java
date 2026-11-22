package com.fashionapp.domain.clothes;

import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.infra.AiEmbedResponse;
import com.fashionapp.infra.AiServerClient;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** FR-07: embedding text, AI failure handling and storage of clothes embeddings. */
@ExtendWith(MockitoExtension.class)
class ClothesEmbedderTest {

    @Mock private AiServerClient aiServerClient;
    @Mock private ClothesEmbeddingStore embeddingStore;

    private ClothesEmbedder embedder;
    private UUID clothesId;

    @BeforeEach
    void setUp() {
        embedder = new ClothesEmbedder(aiServerClient, embeddingStore);
        clothesId = UUID.randomUUID();
    }

    private Clothes clothes(String category, String color, String fit, String material, String pattern, String name) {
        return Clothes.builder().id(clothesId).imageUrl("k").category(category).color(color)
                .fit(fit).material(material).pattern(pattern).name(name).build();
    }

    private static AiEmbedResponse response(List<Double> values) {
        AiEmbedResponse response = new AiEmbedResponse();
        ReflectionTestUtils.setField(response, "embedding", values);
        return response;
    }

    private static List<Double> vector(int size) {
        List<Double> values = new ArrayList<>(Collections.nCopies(size, 0.0));
        values.set(0, 0.5);
        return values;
    }

    @Test
    @DisplayName("임베딩 텍스트는 '카테고리 색상 핏 소재 패턴 이름' 순서다")
    void embeddingText_order() {
        assertThat(ClothesEmbedder.embeddingText(clothes("TOP", "네이비", "슬림", "울", "스트라이프", "네이비 니트")))
                .contains("상의 네이비 슬림 울 스트라이프 네이비 니트");
    }

    @Test
    @DisplayName("null·빈 속성은 임베딩 텍스트에서 빠진다")
    void embeddingText_skipsNulls() {
        assertThat(ClothesEmbedder.embeddingText(clothes("SHOES", "화이트", null, " ", null, "화이트 스니커즈")))
                .contains("신발 화이트 화이트 스니커즈");
    }

    @Test
    @DisplayName("미분류 옷은 임베딩 텍스트가 없고 AI를 부르지 않는다")
    void unclassified_noEmbedding() {
        given(embeddingStore.isAvailable()).willReturn(true);
        Clothes unclassified = clothes("UNCLASSIFIED", null, null, null, null, null);

        assertThat(ClothesEmbedder.embeddingText(unclassified)).isEmpty();
        embedder.refresh(unclassified);

        verify(aiServerClient, never()).embed(anyString());
        verify(embeddingStore).clear(clothesId);
    }

    @Test
    @DisplayName("정상 응답이면 1536차원 벡터를 저장한다")
    void refresh_savesVector() {
        given(embeddingStore.isAvailable()).willReturn(true);
        given(aiServerClient.embed("하의 블랙 블랙 슬랙스")).willReturn(response(vector(1536)));
        given(embeddingStore.save(any(), any())).willReturn(true);

        embedder.refresh(clothes("BOTTOM", "블랙", null, null, null, "블랙 슬랙스"));

        verify(embeddingStore).save(any(UUID.class), any(float[].class));
        verify(embeddingStore, never()).clear(any());
    }

    @Test
    @DisplayName("AI 임베딩이 실패하면 예외 없이 임베딩만 비운다")
    void refresh_aiDown_clearsWithoutThrowing() {
        given(embeddingStore.isAvailable()).willReturn(true);
        given(aiServerClient.embed(anyString())).willThrow(new CustomException(ErrorCode.AI_SERVER_ERROR));

        embedder.refresh(clothes("TOP", "네이비", null, "울", null, "니트"));

        verify(embeddingStore, never()).save(any(), any());
        verify(embeddingStore).clear(clothesId);
    }

    @Test
    @DisplayName("형식이 틀린 응답(없음·차원 불일치)이면 저장하지 않는다")
    void refresh_badResponse_clears() {
        given(embeddingStore.isAvailable()).willReturn(true);
        given(aiServerClient.embed(anyString())).willReturn(response(null), response(vector(3)));

        embedder.refresh(clothes("TOP", "네이비", null, null, null, "니트"));
        embedder.refresh(clothes("TOP", "네이비", null, null, null, "니트"));

        verify(embeddingStore, never()).save(any(), any());
    }

    @Test
    @DisplayName("유한하지 않은 값이 섞이면 벡터로 받지 않는다")
    void toVector_rejectsNonFinite() {
        List<Double> values = vector(1536);
        values.set(10, Double.NaN);
        assertThat(ClothesEmbedder.toVector(values)).isEmpty();
        assertThat(ClothesEmbedder.toVector(vector(1536))).isPresent();
    }

    @Test
    @DisplayName("pgvector가 없으면 AI를 부르지 않는다")
    void refresh_storeUnavailable_skipsAi() {
        given(embeddingStore.isAvailable()).willReturn(false);

        embedder.refresh(clothes("TOP", "네이비", null, null, null, "니트"));

        verify(aiServerClient, never()).embed(anyString());
    }

    @Test
    @DisplayName("FR-11: 살까 말까 후보 옷은 옷장 옷과 같은 문장으로 임베딩하고 저장하지 않는다. 실패하면 빈 값")
    void embedCandidate() {
        given(aiServerClient.embed("상의 블랙 슬림 니트 블랙 knit probe"))
                .willReturn(response(vector(ClothesEmbeddingStore.DIMENSIONS)));
        ClothesClassification candidate = new ClothesClassification(ClothesCategory.TOP, "블랙", "블랙 knit probe",
                "슬림", "니트", null, null);

        assertThat(embedder.embedCandidate(candidate)).hasValueSatisfying(v -> assertThat(v[0]).isEqualTo(0.5f));
        verify(embeddingStore, never()).save(any(), any());

        org.mockito.BDDMockito.willThrow(new CustomException(ErrorCode.AI_SERVER_ERROR))
                .given(aiServerClient).embed(anyString());
        assertThat(embedder.embedCandidate(candidate)).isEmpty();
    }
}
