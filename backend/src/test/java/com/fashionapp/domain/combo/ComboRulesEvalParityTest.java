package com.fashionapp.domain.combo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.fashionapp.domain.clothes.ClothesCategory.BOTTOM;
import static com.fashionapp.domain.clothes.ClothesCategory.TOP;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-24: the combo evaluation tool (evals/combos) re-applies the FR-09 rules in Python. Both sides check the same
 * cases file so the evaluation keeps measuring the engine the app actually uses.
 */
class ComboRulesEvalParityTest {

    /** Gradle runs tests with the backend project directory as the working directory. */
    private static final Path CASES = Path.of("..", "evals", "combos", "parity_cases.json");

    private static ComboItem piece(com.fashionapp.domain.clothes.ClothesCategory category, JsonNode attrs) {
        return new ComboItem(null, category, text(attrs, "color"), text(attrs, "season"), text(attrs, "pattern"),
                text(attrs, "fit"), text(attrs, "material"));
    }

    private static String text(JsonNode attrs, String field) {
        JsonNode value = attrs.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    @Test
    @DisplayName("평가 도구의 두 벌 호환 기대값이 백엔드 규칙 엔진 판정과 같다")
    void evalParityCasesMatchEngine() throws IOException {
        JsonNode cases = new ObjectMapper().readTree(Files.readString(CASES)).get("cases");
        ComboRules rules = ComboTestSupport.defaultRules();

        assertThat(cases.size()).isPositive();
        for (JsonNode c : cases) {
            boolean actual = rules.compatible(piece(TOP, c.get("a")), piece(BOTTOM, c.get("b")));
            assertThat(actual).as("case %s", c).isEqualTo(c.get("compatible").asBoolean());
        }
    }
}
