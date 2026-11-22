package com.fashionapp.domain.clothes;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

/** Parses the availability rules JSON file (format: docs/adr/0014-clothes-status-cooldown.md). Fails fast on a bad file. */
public final class AvailabilityRulesLoader {

    static final int SUPPORTED_VERSION = 1;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    private AvailabilityRulesLoader() {
    }

    record RulesFile(Integer version, Integer laundryDays, Map<String, Integer> cooldownDays) {
    }

    public static AvailabilityRules load(InputStream json) throws IOException {
        RulesFile file = MAPPER.readValue(json, RulesFile.class);
        if (file.version() == null || file.version() != SUPPORTED_VERSION) {
            throw new IllegalArgumentException("unsupported availability rules version: " + file.version());
        }
        if (file.laundryDays() == null) {
            throw new IllegalArgumentException("laundryDays is required");
        }
        Map<ClothesCategory, Integer> cooldown = new EnumMap<>(ClothesCategory.class);
        if (file.cooldownDays() != null) {
            file.cooldownDays().forEach((name, days) -> cooldown.put(parseCategory(name), days));
        }
        return new AvailabilityRules(file.laundryDays(), cooldown);
    }

    private static ClothesCategory parseCategory(String name) {
        return ClothesCategory.parseAssignable(name)
                .orElseThrow(() -> new IllegalArgumentException("unknown category in cooldownDays: " + name));
    }
}
