package com.fashionapp.domain.auth;

import com.fashionapp.domain.user.UserPrincipal;
import com.fashionapp.global.common.ApiResponse;
import com.fashionapp.global.jwt.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PasswordController {

    private final LocalAuthService localAuthService;

    /** 비밀번호 변경 → 새 access/refresh 토큰 (기존 리프레시 토큰은 모두 무효) */
    @PatchMapping("/api/users/me/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, String>>> changePassword(
            @CurrentUser UserPrincipal userPrincipal,
            @RequestBody ChangePasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.success(localAuthService.changePassword(userPrincipal.getId(), request)));
    }
}
