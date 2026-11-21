package com.fashionapp.domain.user;

import com.fashionapp.domain.consent.RequiredAgreements;
import com.fashionapp.global.common.ApiResponse;
import com.fashionapp.global.jwt.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AccountDeletionService accountDeletionService;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile(@CurrentUser UserPrincipal userPrincipal) {
        return ResponseEntity.ok(ApiResponse.success(userService.getMyProfile(userPrincipal.getId())));
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @CurrentUser UserPrincipal userPrincipal,
            @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateProfile(userPrincipal.getId(), request)));
    }

    @DeleteMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteMyAccount(@CurrentUser UserPrincipal userPrincipal) {
        accountDeletionService.deleteAccount(userPrincipal.getId());
        return ResponseEntity.ok(ApiResponse.success());
    }

    // 가상 피팅: 전신 사진이 Hugging Face 공개 서비스로 전송되는 데 대한 동의 (동의 시각을 저장)
    @PostMapping("/me/consents/vton")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserResponse>> grantVtonConsent(@CurrentUser UserPrincipal userPrincipal) {
        return ResponseEntity.ok(ApiResponse.success(userService.grantVtonConsent(userPrincipal.getId())));
    }

    @DeleteMapping("/me/consents/vton")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserResponse>> revokeVtonConsent(@CurrentUser UserPrincipal userPrincipal) {
        return ResponseEntity.ok(ApiResponse.success(userService.revokeVtonConsent(userPrincipal.getId())));
    }

    // "만 14세 이상입니다" 확인과 이용약관·개인정보처리방침 동의 (본문 ageOver14·termsAgreed·privacyAgreed 모두 true여야 함).
    // 확인 전에는 이 경로와 GET·DELETE /api/users/me, /api/auth/**만 쓸 수 있다 (AgeGateInterceptor)
    @PostMapping("/me/age-confirmation")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserResponse>> confirmAge(
            @CurrentUser UserPrincipal userPrincipal,
            @RequestBody(required = false) AgeConfirmationRequest request) {
        RequiredAgreements agreements = request == null ? RequiredAgreements.NONE : request.agreements();
        return ResponseEntity.ok(ApiResponse.success(userService.confirmAge(userPrincipal.getId(), agreements)));
    }
}
