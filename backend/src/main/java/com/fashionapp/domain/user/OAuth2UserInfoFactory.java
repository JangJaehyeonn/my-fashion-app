package com.fashionapp.domain.user;

import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;

import java.util.Map;

public class OAuth2UserInfoFactory {

    public static OAuth2UserInfo getOAuth2UserInfo(String registrationId, Map<String, Object> attributes) {
        return switch (registrationId.toLowerCase()) {
            case "google" -> new GoogleOAuth2UserInfo(attributes);
            case "kakao"  -> new KakaoOAuth2UserInfo(attributes);
            default -> throw new CustomException(ErrorCode.UNSUPPORTED_PROVIDER);
        };
    }
}
