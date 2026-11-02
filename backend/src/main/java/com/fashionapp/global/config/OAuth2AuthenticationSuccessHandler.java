package com.fashionapp.global.config;

import com.fashionapp.domain.user.AuthTokenService;
import com.fashionapp.domain.user.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * OAuth 로그인 성공 → 앱 딥링크로 1회용 코드만 전달. 앱은 POST /api/auth/exchange로 코드를 토큰으로 바꾼다.
 * (예전에는 accessToken·refreshToken을 딥링크 쿼리에 그대로 실어 보내, 같은 스킴을 가로챈 앱이 토큰을 가져갈 수 있었음)
 */
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AuthTokenService authTokenService;

    @Value("${app.oauth2-redirect-base}")
    private String oauth2RedirectBase;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        String targetUrl = UriComponentsBuilder.fromUriString(oauth2RedirectBase + "/oauth2/callback")
                .queryParam("code", authTokenService.issueCode(userPrincipal.getId()))
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
