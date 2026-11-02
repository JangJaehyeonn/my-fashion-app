package com.fashionapp.domain.user;

import com.fashionapp.global.config.OAuth2AuthenticationSuccessHandler;
import com.fashionapp.global.jwt.JwtTokenProvider;
import com.fashionapp.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** OAuth 성공 딥링크의 1회용 코드 발급과 POST /api/auth/exchange 교환을 실제 Redis로 검증 */
class AuthCodeExchangeIntegrationTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired OAuth2AuthenticationSuccessHandler successHandler;
    @Autowired AuthTokenService authTokenService;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired RedisTemplate<String, String> redisTemplate;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .nickname("코드교환테스트")
                .provider(User.AuthProvider.google)
                .providerId("code-test-" + UUID.randomUUID())
                .build());
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete("refresh:" + user.getId());
        if (userRepository.existsById(user.getId())) userRepository.deleteById(user.getId());
    }

    @Test
    @DisplayName("OAuth 성공 리다이렉트에는 토큰 없이 1회용 코드만 실리고, 코드는 60초 TTL로 Redis에 저장된다")
    void successRedirectCarriesOnlyCode() throws Exception {
        String location = oauthSuccessRedirect();

        assertThat(location).startsWith("fashionapp://app/oauth2/callback?code=");
        assertThat(location).doesNotContain("accessToken").doesNotContain("refreshToken");

        String code = codeFrom(location);
        Long ttl = redisTemplate.getExpire(AuthTokenService.CODE_KEY_PREFIX + code);
        assertThat(ttl).isBetween(1L, 60L);
    }

    @Test
    @DisplayName("코드를 교환하면 access/refresh 토큰을 받고, 받은 액세스 토큰으로 API 호출 가능")
    void exchangeSucceeds() throws Exception {
        String code = codeFrom(oauthSuccessRedirect());

        String body = exchange(code)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String accessToken = JsonPath.read(body, "$.data.accessToken");
        String refreshToken = JsonPath.read(body, "$.data.refreshToken");
        assertThat(jwtTokenProvider.getTokenType(accessToken)).isEqualTo(JwtTokenProvider.ACCESS_TYPE);
        assertThat(redisTemplate.opsForValue().get("refresh:" + user.getId())).isEqualTo(refreshToken);

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("코드교환테스트"));
    }

    @Test
    @DisplayName("같은 코드를 두 번 쓰면 두 번째는 401")
    void reuseFails() throws Exception {
        String code = codeFrom(oauthSuccessRedirect());

        exchange(code).andExpect(status().isOk());
        exchange(code)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("만료된 코드는 401")
    void expiredCodeFails() throws Exception {
        String code = "expired-" + UUID.randomUUID();
        // 발급 경로와 같은 키 형식으로 짧은 TTL을 걸어 만료를 흉내 냄 (60초를 기다리지 않기 위해)
        redisTemplate.opsForValue().set(AuthTokenService.CODE_KEY_PREFIX + code, user.getId().toString(), Duration.ofMillis(300));
        Thread.sleep(800);

        exchange(code).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("존재하지 않는 코드·빈 본문은 401")
    void unknownOrMissingCodeFails() throws Exception {
        exchange("no-such-code").andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/exchange")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("코드 발급 후 교환 전에 탈퇴했으면 401")
    void deletedUserCannotExchange() throws Exception {
        String code = authTokenService.issueCode(user.getId());
        userRepository.deleteById(user.getId());

        exchange(code).andExpect(status().isUnauthorized());
    }

    private String oauthSuccessRedirect() throws Exception {
        UserPrincipal principal = UserPrincipal.create(user);
        MockHttpServletResponse response = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        return response.getRedirectedUrl();
    }

    private static String codeFrom(String location) {
        return UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("code");
    }

    private ResultActions exchange(String code) throws Exception {
        return mockMvc.perform(post("/api/auth/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"));
    }
}
