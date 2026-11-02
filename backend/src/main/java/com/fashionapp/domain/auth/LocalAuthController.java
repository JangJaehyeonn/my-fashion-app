package com.fashionapp.domain.auth;

import com.fashionapp.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 아이디·비밀번호 가입/로그인 (/api/auth/** 는 인증 없이 접근 가능) */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class LocalAuthController {

    private final LocalAuthService localAuthService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Map<String, String>>> signup(@RequestBody SignupRequest request) {
        return ResponseEntity.ok(ApiResponse.success(localAuthService.signup(request)));
    }

    @GetMapping("/check-id")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> checkId(@RequestParam("loginId") String loginId) {
        return ResponseEntity.ok(ApiResponse.success(Map.of("available", localAuthService.isLoginIdAvailable(loginId))));
    }
}
