package com.fashionapp.domain.combo;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/** Parses the combo rules JSON file (format: docs/adr/0008-combo-count-rules.md). Fails fast on a malformed file. */
public final class ComboRulesLoader {

    static final int SUPPORTED_VERSION = 1;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    private ComboRulesLoader() {
    }

    record StyleConflictsFile(String attribute, List<List<String>> pairs) {
    }

    record RulesFile(Integer version, List<String> neutralColors, List<List<String>> colorConflicts,
                     List<List<String>> seasonConflicts, StyleConflictsFile styleConflicts) {
    }

    public static ComboRules load(InputStream json) throws IOException {
        RulesFile file = MAPPER.readValue(json, RulesFile.class);
        if (file.version() == null || file.version() != SUPPORTED_VERSION) {
            throw new IllegalArgumentException("unsupported combo rules version: " + file.version());
        }
        StyleConflictsFile style = file.styleConflicts() == null
                ? new StyleConflictsFile("pattern", List.of()) : file.styleConflicts();
        ComboRules.StyleAttribute attribute = ComboRules.StyleAttribute.valueOf(
                (style.attribute() == null ? "pattern" : style.attribute()).trim().toUpperCase(Locale.ROOT));
        return new ComboRules(orEmpty(file.neutralColors()), orEmpty(file.colorConflicts()),
                orEmpty(file.seasonConflicts()), attribute, orEmpty(style.pairs()));
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
