package com.fashionapp.domain.clothes;

import java.util.Locale;
import java.util.Optional;

/** FR-27 care status of a piece. A null column (rows before FR-27) is AVAILABLE. */
public enum ClothesStatus {
    AVAILABLE, LAUNDRY;

    /** Lenient read of the stored value: null or unknown names are AVAILABLE. */
    public static ClothesStatus from(String value) {
        return parse(value).orElse(AVAILABLE);
    }

    /** Strict parse for request values. */
    public static Optional<ClothesStatus> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
