package com.fashionapp.domain.clothes;

import java.util.Optional;

/** Season a garment is mainly worn in (FR-07). Stored as the enum name; unknown means null. */
public enum ClothesSeason {
    SPRING, SUMMER, FALL, WINTER;

    /** Lenient parse: blank or unknown values become empty (the attribute stays unknown). */
    public static Optional<ClothesSeason> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
