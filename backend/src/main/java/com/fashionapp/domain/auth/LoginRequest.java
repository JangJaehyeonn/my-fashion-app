package com.fashionapp.domain.auth;

import lombok.Getter;
import lombok.NoArgsConstructor;

// toString을 만들지 않음 — 비밀번호가 로그에 섞이지 않게 (SignupRequest와 동일)
@Getter
@NoArgsConstructor
public class LoginRequest {
    private String loginId;
    private String password;
}
