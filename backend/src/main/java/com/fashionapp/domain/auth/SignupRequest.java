package com.fashionapp.domain.auth;

import com.fashionapp.domain.consent.RequiredAgreements;
import lombok.Getter;
import lombok.NoArgsConstructor;

// toString을 만들지 않음 — 실수로 로그에 찍혀도 비밀번호가 노출되지 않게 (Object 기본 toString)
@Getter
@NoArgsConstructor
public class SignupRequest {
    private String loginId;
    private String password;
    private String nickname;
    // "만 14세 이상입니다" 체크 — true가 아니면 가입 거절
    private Boolean ageOver14;
    // 이용약관·개인정보처리방침 동의 — 둘 다 true가 아니면 가입 거절 (FR-05)
    private Boolean termsAgreed;
    private Boolean privacyAgreed;

    public RequiredAgreements agreements() {
        return new RequiredAgreements(termsAgreed, privacyAgreed, ageOver14);
    }
}
