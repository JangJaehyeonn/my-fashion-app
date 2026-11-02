package com.fashionapp.global.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * OAuth 로그인을 앱이 시작했는지 표시하고, 로그인 결과(특히 실패) 처리 시점까지 그 표시를 전달한다.
 *
 * <p>앱은 {@code /oauth2/authorization/{provider}?client=app}으로 로그인을 시작한다. 이 값을 OAuth2 인가 요청의
 * attributes에 담아 두면(세션 저장), 콜백에서 Spring이 인가 요청을 세션에서 꺼내 지울 때 요청 속성으로 옮겨 두어
 * 실패 핸들러가 "앱 요청이면 앱 딥링크로" 보낼 수 있다.
 */
public final class OAuth2ClientMarker {

    static final String CLIENT_PARAM = "client";
    static final String APP_CLIENT = "app";
    private static final String REMOVED_REQUEST_ATTR = OAuth2ClientMarker.class.getName() + ".REMOVED_REQUEST";

    private OAuth2ClientMarker() {}

    /** 이번 콜백 요청이 앱에서 시작한 로그인의 결과인지 (인가 요청을 찾지 못한 경우엔 false) */
    public static boolean isAppRequest(HttpServletRequest request) {
        Object removed = request.getAttribute(REMOVED_REQUEST_ATTR);
        return removed instanceof OAuth2AuthorizationRequest authRequest
                && APP_CLIENT.equals(authRequest.getAttribute(CLIENT_PARAM));
    }

    /** ?client=app이면 인가 요청 attributes에 표시를 남기는 resolver */
    static OAuth2AuthorizationRequestResolver resolver(ClientRegistrationRepository repository) {
        DefaultOAuth2AuthorizationRequestResolver delegate =
                new DefaultOAuth2AuthorizationRequestResolver(repository, "/oauth2/authorization");
        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                return mark(request, delegate.resolve(request));
            }

            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
                return mark(request, delegate.resolve(request, clientRegistrationId));
            }
        };
    }

    private static OAuth2AuthorizationRequest mark(HttpServletRequest request, OAuth2AuthorizationRequest authRequest) {
        if (authRequest == null || !APP_CLIENT.equals(request.getParameter(CLIENT_PARAM))) {
            return authRequest;
        }
        return OAuth2AuthorizationRequest.from(authRequest)
                .attributes(attrs -> attrs.put(CLIENT_PARAM, APP_CLIENT))
                .build();
    }

    /** 세션 저장소에 위임하되, 콜백에서 꺼내 지운 인가 요청을 요청 속성에 남겨 실패 핸들러가 볼 수 있게 함 */
    static AuthorizationRequestRepository<OAuth2AuthorizationRequest> repository() {
        HttpSessionOAuth2AuthorizationRequestRepository delegate = new HttpSessionOAuth2AuthorizationRequestRepository();
        return new AuthorizationRequestRepository<>() {
            @Override
            public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
                return delegate.loadAuthorizationRequest(request);
            }

            @Override
            public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                                 HttpServletRequest request, HttpServletResponse response) {
                delegate.saveAuthorizationRequest(authorizationRequest, request, response);
            }

            @Override
            public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                        HttpServletResponse response) {
                OAuth2AuthorizationRequest removed = delegate.removeAuthorizationRequest(request, response);
                if (removed != null) {
                    request.setAttribute(REMOVED_REQUEST_ATTR, removed);
                }
                return removed;
            }
        };
    }
}
