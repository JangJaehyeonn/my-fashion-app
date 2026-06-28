package com.fashionapp.domain.user;

import com.fashionapp.global.common.ApiResponse;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.global.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    // Google/Kakao 로그인은 Spring Security OAuth2 리다이렉트 방식으로 처리
    // GET /oauth2/authorization/google
    // GET /oauth2/authorization/kakao

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Map<String, String>>> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        UUID userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        String storedToken = redisTemplate.opsForValue().get("refresh:" + userId);

        if (!refreshToken.equals(storedToken)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        String newAccessToken = jwtTokenProvider.createAccessToken(userId);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);

        redisTemplate.opsForValue().set(
                "refresh:" + userId,
                newRefreshToken,
                refreshTokenExpiry,
                TimeUnit.MILLISECONDS
        );

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "accessToken", newAccessToken,
                "refreshToken", newRefreshToken
        )));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) Map<String, String> body) {
        if (body != null) {
            String refreshToken = body.get("refreshToken");
            if (refreshToken != null && jwtTokenProvider.validateToken(refreshToken)) {
                UUID userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
                redisTemplate.delete("refresh:" + userId);
            }
        }

        return ResponseEntity.ok(ApiResponse.success());
    }
}
