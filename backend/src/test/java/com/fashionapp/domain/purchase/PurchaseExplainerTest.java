package com.fashionapp.domain.purchase;

import com.fashionapp.domain.clothes.ClothesCategory;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.infra.AiPurchaseExplainRequest;
import com.fashionapp.infra.AiPurchaseExplainResponse;
import com.fashionapp.infra.AiServerClient;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/** FR-11: AI picks only from the valid combos, AI sentences are validated, failures fall back to rule sentences. */
@ExtendWith(MockitoExtension.class)
class PurchaseExplainerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Mock private AiServerClient aiServerClient;

    private final UUID b1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID b2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final UUID s1 = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private final UUID s2 = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private final UUID similarId = UUID.fromString("00000000-0000-0000-0000-000000000005");

    private PurchaseContext context(PurchaseVerdict verdict, List<PurchaseCheckResponse.Similar> similar) {
        PurchaseCheckResponse.Item item = new PurchaseCheckResponse.Item(ClothesCategory.TOP, "블랙", "블랙 hoodie",
                null, null, null, null);
        return new PurchaseContext(item, verdict, 4, 8, 12, similar, Map.of("TOP", 2, "BOTTOM", 2, "SHOES", 2),
                List.of(List.of(b1, s1), List.of(b1, s2), List.of(b2, s1), List.of(b2, s2)), 2,
                Map.of(b1, "하의 블랙 슬랙스"));
    }

    private static AiPurchaseExplainResponse response(String json) throws Exception {
        return MAPPER.readValue(json, AiPurchaseExplainResponse.class);
    }

    private String picksJson(String... combos) {
        return String.join(",", combos);
    }

    private String combo(UUID... ids) {
        StringBuilder sb = new StringBuilder("{\"clothesIds\":[");
        for (int i = 0; i < ids.length; i++) {
            sb.append(i > 0 ? "," : "").append('"').append(ids[i]).append('"');
        }
        return sb.append("],\"label\":\"출근\"}").toString();
    }

    @Test
    @DisplayName("AI가 유효 조합 목록에 없는 코디를 고르면 버리고, 순서를 뒤섞어 보낸 조합은 받아들인다")
    void dropsPicksOutsideCandidates() throws Exception {
        given(aiServerClient.explainPurchase(any())).willReturn(response("{\"picks\":["
                + picksJson(combo(b1, b2, s1), combo(s2, b2), combo(b1, s1), combo(b1, s1)) + "],"
                + "\"headline\":\"한 줄\",\"reasons\":[\"이유1\",\"이유2\",\"이유3\"],\"gap\":\"빈틈\"}"));

        PurchaseExplainer.Explanation result = new PurchaseExplainer(aiServerClient)
                .explain(context(PurchaseVerdict.THINK, List.of()));

        assertThat(result.outfits()).extracting(PurchaseCheckResponse.Outfit::clothesIds)
                .containsExactly(List.of(b2, s2), List.of(b1, s1));
        assertThat(result.outfits()).extracting(PurchaseCheckResponse.Outfit::label).containsOnly("출근");
        assertThat(result.headline()).isEqualTo("한 줄");
        assertThat(result.reasons()).containsExactly("이유1", "이유2", "이유3");
        assertThat(result.gap()).isEqualTo("빈틈");
    }

    @Test
    @DisplayName("AI 요청에는 규칙 판정·+N·비슷한 옷·분포·유효 조합이 그대로 간다")
    void sendsRuleResults() {
        given(aiServerClient.explainPurchase(any())).willThrow(new CustomException(ErrorCode.AI_SERVER_ERROR));
        PurchaseCheckResponse.Similar similar = new PurchaseCheckResponse.Similar(similarId, "화이트 knit", 0.9, 2,
                LocalDate.of(2026, 11, 1), null);

        new PurchaseExplainer(aiServerClient).explain(context(PurchaseVerdict.SIMILAR, List.of(similar)));

        ArgumentCaptor<AiPurchaseExplainRequest> sent = ArgumentCaptor.forClass(AiPurchaseExplainRequest.class);
        verify(aiServerClient).explainPurchase(sent.capture());
        AiPurchaseExplainRequest request = sent.getValue();
        assertThat(request.verdict()).isEqualTo("SIMILAR");
        assertThat(request.plus()).isEqualTo(4);
        assertThat(request.comboCountBefore()).isEqualTo(8);
        assertThat(request.comboCountAfter()).isEqualTo(12);
        assertThat(request.similar()).containsExactly(
                new AiPurchaseExplainRequest.Similar(similarId.toString(), "화이트 knit", 0.9, 2, "2026-11-01"));
        assertThat(request.distribution()).containsEntry("TOP", 2);
        assertThat(request.candidates()).hasSize(4);
        assertThat(request.candidates().get(0).labels()).containsExactly("하의 블랙 슬랙스", "");
        assertThat(request.item().category()).isEqualTo("TOP");
    }

    @Test
    @DisplayName("AI 호출이 실패하면 규칙 기반 기본 문장과 앞 3개 조합으로 응답한다")
    void fallsBackWhenAiFails() {
        given(aiServerClient.explainPurchase(any())).willThrow(new CustomException(ErrorCode.AI_SERVER_ERROR));

        PurchaseExplainer.Explanation result = new PurchaseExplainer(aiServerClient)
                .explain(context(PurchaseVerdict.THINK, List.of()));

        assertThat(result.headline()).isEqualTo("늘어나는 코디는 4개예요");
        assertThat(result.reasons()).hasSize(3).allSatisfy(r -> assertThat(r).isNotBlank());
        assertThat(result.reasons().get(0)).isEqualTo("이 옷을 더하면 코디가 8개에서 12개가 돼요");
        assertThat(result.gap()).isNull();
        assertThat(result.outfits()).hasSize(3).extracting(PurchaseCheckResponse.Outfit::label).containsOnly("데일리");
    }

    @Test
    @DisplayName("형식이 틀린 응답(문장 없음, 이유 3개 미만, 빈 이유)이면 기본 문장")
    void fallsBackOnMalformedSentences() throws Exception {
        PurchaseExplainer explainer = new PurchaseExplainer(aiServerClient);
        for (String json : List.of("{\"unexpected\":true}",
                "{\"headline\":\"한 줄\",\"reasons\":[\"이유1\",\"이유2\"]}",
                "{\"headline\":\" \",\"reasons\":[\"이유1\",\"이유2\",\"이유3\"]}",
                "{\"headline\":\"한 줄\",\"reasons\":[\"이유1\",\"\",\"이유3\"]}")) {
            given(aiServerClient.explainPurchase(any())).willReturn(response(json));
            PurchaseExplainer.Explanation result = explainer.explain(context(PurchaseVerdict.BUY, List.of()));
            assertThat(result.headline()).as(json).isEqualTo("코디가 4개 늘어나는 옷이에요");
            assertThat(result.reasons()).as(json).hasSize(3).allSatisfy(r -> assertThat(r).isNotBlank());
        }
    }

    @Test
    @DisplayName("AI가 다른 판정을 담아 보내면 그 문장을 버리고 기본 문장을 쓴다")
    void ignoresSentencesOfDisagreeingAi() throws Exception {
        given(aiServerClient.explainPurchase(any())).willReturn(response("{\"verdict\":\"BUY\","
                + "\"headline\":\"무조건 사세요\",\"reasons\":[\"a\",\"b\",\"c\"]}"));

        PurchaseExplainer.Explanation result = new PurchaseExplainer(aiServerClient)
                .explain(context(PurchaseVerdict.THINK, List.of()));

        assertThat(result.headline()).isEqualTo("늘어나는 코디는 4개예요");
    }

    @Test
    @DisplayName("비슷한 옷별 note는 비슷한 옷 목록에 있는 id만 받는다. SIMILAR 기본 문장은 가장 비슷한 옷을 말한다")
    void notesAndSimilarFallback() throws Exception {
        PurchaseCheckResponse.Similar similar = new PurchaseCheckResponse.Similar(similarId, "화이트 knit", 0.92, 0,
                null, null);
        given(aiServerClient.explainPurchase(any())).willReturn(response("{\"headline\":\"한 줄\","
                + "\"reasons\":[\"a\",\"b\",\"c\"],\"similarNotes\":[{\"id\":\"" + similarId + "\",\"note\":\"목이 달라요\"},"
                + "{\"id\":\"" + b1 + "\",\"note\":\"남의 것\"}]}"));
        PurchaseExplainer explainer = new PurchaseExplainer(aiServerClient);

        assertThat(explainer.explain(context(PurchaseVerdict.SIMILAR, List.of(similar))).notes())
                .isEqualTo(Map.of(similarId, "목이 달라요"));

        given(aiServerClient.explainPurchase(any())).willThrow(new CustomException(ErrorCode.AI_SERVER_TIMEOUT));
        PurchaseExplainer.Explanation fallback = explainer.explain(context(PurchaseVerdict.SIMILAR, List.of(similar)));
        assertThat(fallback.headline()).isEqualTo("옷장에 비슷한 옷이 있어요");
        assertThat(fallback.reasons()).containsExactly("가장 비슷한 옷은 화이트 knit이에요 (비슷한 정도 92%)",
                "가장 비슷한 옷은 아직 입은 기록이 없어요", "이 옷을 더하면 코디가 8개에서 12개가 돼요");
        assertThat(fallback.notes()).isEmpty();
    }
}
