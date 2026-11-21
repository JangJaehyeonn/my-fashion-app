package com.fashionapp.domain.consent;

import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;

/**
 * The three agreements every account needs before using the service (FR-05): terms of service, privacy policy,
 * and the "14 or older" check. Used by id/password signup and by the social first-login confirmation.
 * A missing (null) value counts as not agreed.
 */
public record RequiredAgreements(Boolean termsAgreed, Boolean privacyAgreed, Boolean ageOver14) {

    public static final RequiredAgreements NONE = new RequiredAgreements(null, null, null);

    /** Throws 400 unless all three are true. The age check comes first so its existing message stays the same. */
    public void requireAll() {
        if (!Boolean.TRUE.equals(ageOver14)) {
            throw new CustomException(ErrorCode.AGE_REQUIRED_FOR_SIGNUP);
        }
        if (!Boolean.TRUE.equals(termsAgreed) || !Boolean.TRUE.equals(privacyAgreed)) {
            throw new CustomException(ErrorCode.TERMS_AGREEMENT_REQUIRED);
        }
    }
}
