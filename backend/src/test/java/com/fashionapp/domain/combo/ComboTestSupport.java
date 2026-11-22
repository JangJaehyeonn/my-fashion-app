package com.fashionapp.domain.combo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** Builds the combo engine from the real default rules file (src/main/resources/rules/combo-rules.json). */
public final class ComboTestSupport {

    private ComboTestSupport() {
    }

    public static ComboRules defaultRules() {
        try (InputStream in = ComboTestSupport.class.getResourceAsStream("/rules/combo-rules.json")) {
            return ComboRulesLoader.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static ComboEngine defaultEngine() {
        return new ComboEngine(defaultRules());
    }

    public static ComboCountService defaultService() {
        return new ComboCountService(defaultEngine());
    }
}
