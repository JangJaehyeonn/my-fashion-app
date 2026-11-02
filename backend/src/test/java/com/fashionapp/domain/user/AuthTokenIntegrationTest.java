package com.fashionapp.domain.user;

import com.fashionapp.global.jwt.JwtTokenProvider;
import com.fashionapp.support.IntegrationTest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 토큰 종류(typ) 구분과 로그아웃 후 재발급 차단을 실제 필터 체인 + Redis로 검증 */
class AuthTokenIntegrationTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired RedisTemplate<String, String> redisTemplate;

    @Value("${jwt.secret}") String jwtSecret;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .nickname("토큰테스트")
                .provider(User.AuthProvider.google)
                .providerId("token-test-" + UUID.randomUUID())
                .build());
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete("refresh:" + user.getId());
        userRepository.deleteById(user.getId());
    }

    @Test
    @DisplayName("액세스 토큰으로는 일반 API를 호출할 수 있다")
    void accessTokenAuthenticates() throws Exception {
        mockMvc.perform(get("/api/users/me").header("Authorization", bearer(jwtTokenProvider.createAccessToken(user.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("토큰테스트"));
    }

    @Test
    @DisplayName("리프레시 토큰을 Bearer로 쓰면 401")
    void refreshTokenRejectedAsBearer() throws Exception {
        mockMvc.perform(get("/api/users/me").header("Authorization", bearer(jwtTokenProvider.createRefreshToken(user.getId()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("typ claim이 없는 옛 액세스 토큰은 401 (앱이 리프레시로 새 토큰을 받도록)")
    void legacyAccessTokenRejected() throws Exception {
        mockMvc.perform(get("/api/users/me").header("Authorization", bearer(legacyToken(user.getId()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("액세스 토큰으로는 재발급 불가")
    void refreshWithAccessTokenFails() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        // Redis에 같은 값이 있더라도 typ=access면 거부되는지 확인
        redisTemplate.opsForValue().set("refresh:" + user.getId(), accessToken, 1, TimeUnit.MINUTES);

        mockMvc.perform(refresh(accessToken)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("리프레시 토큰으로 재발급하면 새 토큰은 각각 access/refresh 타입")
    void refreshIssuesTypedTokens() throws Exception {
        String refreshToken = storeRefreshToken(jwtTokenProvider.createRefreshToken(user.getId()));

        String body = mockMvc.perform(refresh(refreshToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String newAccess = com.jayway.jsonpath.JsonPath.read(body, "$.data.accessToken");
        String newRefresh = com.jayway.jsonpath.JsonPath.read(body, "$.data.refreshToken");
        assertThat(jwtTokenProvider.getTokenType(newAccess)).isEqualTo(JwtTokenProvider.ACCESS_TYPE);
        assertThat(jwtTokenProvider.getTokenType(newRefresh)).isEqualTo(JwtTokenProvider.REFRESH_TYPE);
    }

    @Test
    @DisplayName("typ이 없는 옛 리프레시 토큰도 Redis와 일치하면 재발급된다 (배포 직후 강제 로그아웃 방지)")
    void legacyRefreshTokenStillRefreshes() throws Exception {
        String legacyRefresh = storeRefreshToken(legacyToken(user.getId()));

        mockMvc.perform(refresh(legacyRefresh)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그아웃(본문에 리프레시 토큰) 후에는 같은 리프레시 토큰으로 재발급 실패")
    void refreshFailsAfterLogout() throws Exception {
        String refreshToken = storeRefreshToken(jwtTokenProvider.createRefreshToken(user.getId()));

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(refreshToken)))
                .andExpect(status().isOk());
        assertThat(redisTemplate.hasKey("refresh:" + user.getId())).isFalse();

        mockMvc.perform(refresh(refreshToken)).andExpect(status().isUnauthorized());
    }

    private String storeRefreshToken(String token) {
        redisTemplate.opsForValue().set("refresh:" + user.getId(), token, 1, TimeUnit.MINUTES);
        return token;
    }

    // 이 변경 이전 형식(typ claim 없음)의 토큰
    private String legacyToken(UUID userId) {
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret)))
                .compact();
    }

    private static org.springframework.test.web.servlet.RequestBuilder refresh(String refreshToken) {
        return post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshJson(refreshToken));
    }

    private static String refreshJson(String refreshToken) {
        return "{\"refreshToken\":\"" + refreshToken + "\"}";
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
