package com.fashionapp.global.config;

import com.fashionapp.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OAuth 로그인 실패 리다이렉트. Google에 실제로 접속하지 않고, 인가 요청(/oauth2/authorization/google) 뒤
 * 사용자가 동의를 거부한 것처럼 콜백에 error=access_denied를 보내 실패 핸들러까지의 흐름을 검증한다.
 */
class OAuth2FailureRedirectIntegrationTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;

    @Value("${app.frontend-url}") String frontendUrl;

    @Test
    @DisplayName("앱(?client=app)에서 시작한 로그인이 실패하면 앱 딥링크로 오류 코드를 전달")
    void appLoginFailureRedirectsToApp() throws Exception {
        String location = failedLogin("/oauth2/authorization/google?client=app", "access_denied");

        assertThat(location).isEqualTo("fashionapp://app/oauth2/callback?error=access_denied");
    }

    @Test
    @DisplayName("client 표시 없이 시작한 로그인(웹)이 실패하면 웹 프런트엔드로")
    void webLoginFailureRedirectsToWeb() throws Exception {
        String location = failedLogin("/oauth2/authorization/google", "access_denied");

        assertThat(location).isEqualTo(frontendUrl + "/oauth2/callback?error=access_denied");
    }

    @Test
    @DisplayName("Kakao도 같은 방식으로 앱에 전달")
    void kakaoAppLoginFailure() throws Exception {
        String location = failedLogin("/oauth2/authorization/kakao?client=app", "access_denied");

        assertThat(location).isEqualTo("fashionapp://app/oauth2/callback?error=access_denied");
    }

    @Test
    @DisplayName("오류 코드가 형식에 안 맞으면 login_failed로 대체, 예외 메시지는 URL에 넣지 않음")
    void errorCodeIsSanitized() {
        assertThat(OAuth2AuthenticationFailureHandler.errorCode(
                new OAuth2AuthenticationException(new OAuth2Error("server_error"), "내부 메시지")))
                .isEqualTo("server_error");
        assertThat(OAuth2AuthenticationFailureHandler.errorCode(
                new OAuth2AuthenticationException(new OAuth2Error("Bad Code<script>"))))
                .isEqualTo("login_failed");
        assertThat(OAuth2AuthenticationFailureHandler.errorCode(new BadCredentialsException("x")))
                .isEqualTo("login_failed");
    }

    /** 인가 요청을 시작한 뒤(세션 유지) 공급자가 오류로 콜백한 상황을 재현하고, 최종 리다이렉트 위치를 반환 */
    private String failedLogin(String authorizationPath, String error) throws Exception {
        MockHttpSession session = new MockHttpSession();
        String providerUrl = mockMvc.perform(get(authorizationPath).session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        // getQueryParams()는 디코딩하지 않으므로(state의 '='가 %3D로 남음) 디코딩해서 보냄
        String state = java.net.URLDecoder.decode(
                UriComponentsBuilder.fromUriString(providerUrl).build().getQueryParams().getFirst("state"),
                java.nio.charset.StandardCharsets.UTF_8);
        String registrationId = authorizationPath.contains("kakao") ? "kakao" : "google";

        return mockMvc.perform(get("/login/oauth2/code/" + registrationId)
                        .param("error", error)
                        .param("state", state)
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
    }
}
