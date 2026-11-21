package com.fashionapp.domain.user;

import com.fashionapp.domain.consent.RequiredAgreements;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** POST /api/users/me/age-confirmation body: social first login confirms age and agrees to terms/privacy together (FR-05). */
@Getter
@NoArgsConstructor
public class AgeConfirmationRequest {
    private Boolean ageOver14;
    private Boolean termsAgreed;
    private Boolean privacyAgreed;

    public RequiredAgreements agreements() {
        return new RequiredAgreements(termsAgreed, privacyAgreed, ageOver14);
    }
}
