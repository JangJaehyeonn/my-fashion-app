package com.fashionapp.global.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * OAuth 로그인 실패 → 앱에서 시작한 로그인이면 앱 딥링크(fashionapp://app/oauth2/callback?error=...)로,
 * 그 밖(웹, 또는 인가 요청을 찾지 못한 경우)이면 웹 프런트엔드로 보낸다.
 *
 * <p>error 값은 예외 메시지가 아니라 OAuth2 오류 코드(예: access_denied)만 전달한다 — 내부 메시지를 URL에 노출하지 않기 위함.
 * 앱은 access_denied면 "로그인을 취소했어요", 그 밖이면 일반 실패 안내를 보여준다.
 */
@Slf4j
@Component
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    static final String DEFAULT_ERROR = "login_failed";

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.oauth2-redirect-base}")
    private String oauth2RedirectBase;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String error = errorCode(exception);
        boolean fromApp = OAuth2ClientMarker.isAppRequest(request);
        log.warn("OAuth2 authentication failed: error={} app={} message={}", error, fromApp, exception.getMessage());

        String base = fromApp ? oauth2RedirectBase : frontendUrl;
        String targetUrl = UriComponentsBuilder.fromUriString(base + "/oauth2/callback")
                .queryParam("error", error)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    // OAuth2 오류 코드만, 영문 소문자·숫자·밑줄로 제한 (그 밖이면 login_failed)
    static String errorCode(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauth2Exception
                && oauth2Exception.getError() != null
                && oauth2Exception.getError().getErrorCode() != null
                && oauth2Exception.getError().getErrorCode().matches("[a-z0-9_]{1,64}")) {
            return oauth2Exception.getError().getErrorCode();
        }
        return DEFAULT_ERROR;
    }
}
