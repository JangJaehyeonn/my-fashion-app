package com.fashionapp.domain.consent;

/**
 * Kinds of consent kept in consent_records. Stored by name (STRING).
 *
 * <p>{@code currentVersion} is the version of the document/notice the user agrees to now.
 * Bump it when the text changes; existing rows keep the version they agreed to.
 */
public enum ConsentType {
    TERMS("1"),
    PRIVACY("1"),
    AGE_OVER_14("1"),
    VTON("1");

    /** Version written for rows migrated from users.age_confirmed_at / users.vton_consent_at (text unknown). */
    public static final String LEGACY_VERSION = "legacy";

    private final String currentVersion;

    ConsentType(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public String currentVersion() {
        return currentVersion;
    }
}
