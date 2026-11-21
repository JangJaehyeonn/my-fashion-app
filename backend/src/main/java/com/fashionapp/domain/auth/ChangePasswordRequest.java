package com.fashionapp.domain.auth;

import lombok.Getter;
import lombok.NoArgsConstructor;

// toString을 만들지 않음 — 비밀번호가 로그에 섞이지 않게
@Getter
@NoArgsConstructor
public class ChangePasswordRequest {
    private String currentPassword;
    private String newPassword;
}
