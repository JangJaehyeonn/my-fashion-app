package com.fashionapp.domain.auth;

import lombok.Getter;
import lombok.NoArgsConstructor;

// toString을 만들지 않음 — 실수로 로그에 찍혀도 비밀번호가 노출되지 않게 (Object 기본 toString)
@Getter
@NoArgsConstructor
public class SignupRequest {
    private String loginId;
    private String password;
    private String nickname;
}
