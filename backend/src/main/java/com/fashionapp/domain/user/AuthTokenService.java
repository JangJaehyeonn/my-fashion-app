package com.fashionapp.domain.user;

import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.global.jwt.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 로그인 토큰 발급과 OAuth 딥링크용 1회용 코드.
 *
 * <p>OAuth 로그인 성공 시 토큰을 딥링크(fashionapp://app/oauth2/callback?...)에 직접 싣지 않고, 짧게 사는 1회용 코드만 싣는다.
 * 같은 스킴을 등록한 다른 앱이 딥링크를 가로채거나 로그 등에 URL이 남아도 코드는 {@link #exchangeCode}로 한 번만,
 * 만료(기본 60초) 전에만 토큰으로 바꿀 수 있다.
 */
@Service
public class AuthTokenService {

    static final String CODE_KEY_PREFIX = "auth:code:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;
    private final long refreshTokenExpiry;
    private final Duration exchangeCodeTtl;

    public AuthTokenService(JwtTokenProvider jwtTokenProvider,
                            RedisTemplate<String, String> redisTemplate,
                            UserRepository userRepository,
                            @Value("${jwt.refresh-token-expiry}") long refreshTokenExpiry,
                            @Value("${app.auth.exchange-code-ttl:60s}") Duration exchangeCodeTtl) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
        this.refreshTokenExpiry = refreshTokenExpiry;
        this.exchangeCodeTtl = exchangeCodeTtl;
    }

    /** 액세스·리프레시 토큰을 새로 발급하고 리프레시 토큰을 Redis에 저장 (사용자당 1개, 이전 토큰은 무효화) */
    public Map<String, String> issueTokens(UUID userId) {
        String accessToken = jwtTokenProvider.createAccessToken(userId);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);
        redisTemplate.opsForValue().set("refresh:" + userId, refreshToken, refreshTokenExpiry, TimeUnit.MILLISECONDS);
        return Map.of("accessToken", accessToken, "refreshToken", refreshToken);
    }

    /** OAuth 성공 시 딥링크에 실을 1회용 코드 (256비트 난수, URL-safe) */
    public String issueCode(UUID userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redisTemplate.opsForValue().set(CODE_KEY_PREFIX + code, userId.toString(), exchangeCodeTtl);
        return code;
    }

    /** 코드 → 토큰. GETDEL로 읽으면서 바로 지워 동시에 두 번 요청해도 한 번만 성공한다. 없거나 만료·사용됨이면 401 */
    public Map<String, String> exchangeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_AUTH_CODE);
        }
        String userId = redisTemplate.opsForValue().getAndDelete(CODE_KEY_PREFIX + code);
        if (userId == null) {
            throw new CustomException(ErrorCode.INVALID_AUTH_CODE);
        }
        UUID id = UUID.fromString(userId);
        // 코드 발급과 교환 사이에 탈퇴한 경우
        if (!userRepository.existsById(id)) {
            throw new CustomException(ErrorCode.INVALID_AUTH_CODE);
        }
        return issueTokens(id);
    }
}
