package com.fashionapp.domain.user;

import com.fashionapp.global.common.ApiResponse;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.global.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;
    private final AuthTokenService authTokenService;

    // Google/Kakao 로그인은 Spring Security OAuth2 리다이렉트 방식으로 처리
    // GET /oauth2/authorization/google
    // GET /oauth2/authorization/kakao

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Map<String, String>>> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");

        // 액세스 토큰으로는 재발급 불가. typ이 없는 옛 리프레시 토큰은 아래 Redis 일치 검사로만 판단
        // (Redis에는 리프레시 토큰만 저장되므로 일치하면 리프레시 토큰임이 보장됨)
        if (!jwtTokenProvider.validateToken(refreshToken)
                || JwtTokenProvider.ACCESS_TYPE.equals(jwtTokenProvider.getTokenType(refreshToken))) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        UUID userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        String storedToken = redisTemplate.opsForValue().get("refresh:" + userId);

        if (!refreshToken.equals(storedToken)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        // 탈퇴한 사용자: 탈퇴 시 Redis 토큰 삭제가 실패했더라도 재발급되지 않게 함
        if (!userRepository.existsById(userId)) {
            redisTemplate.delete("refresh:" + userId);
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        return ResponseEntity.ok(ApiResponse.success(authTokenService.issueTokens(userId)));
    }

    /**
     * OAuth 로그인 성공 딥링크(fashionapp://app/oauth2/callback?code=...)로 받은 1회용 코드를 토큰으로 교환.
     * 코드는 60초 안에 한 번만 쓸 수 있고, 만료·재사용·없는 코드는 401(INVALID_AUTH_CODE).
     */
    @PostMapping("/exchange")
    public ResponseEntity<ApiResponse<Map<String, String>>> exchange(@RequestBody(required = false) Map<String, String> body) {
        String code = body != null ? body.get("code") : null;
        return ResponseEntity.ok(ApiResponse.success(authTokenService.exchangeCode(code)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) Map<String, String> body) {
        if (body != null) {
            String refreshToken = body.get("refreshToken");
            if (refreshToken != null && jwtTokenProvider.validateToken(refreshToken)
                    && !JwtTokenProvider.ACCESS_TYPE.equals(jwtTokenProvider.getTokenType(refreshToken))) {
                UUID userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
                redisTemplate.delete("refresh:" + userId);
            }
        }

        return ResponseEntity.ok(ApiResponse.success());
    }
}
