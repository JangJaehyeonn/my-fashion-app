package com.fashionapp.domain.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuth2UserInfo userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(registrationId, oAuth2User.getAttributes());

        User user = saveOrUpdate(userInfo, registrationId);
        return UserPrincipal.create(user, oAuth2User.getAttributes());
    }

    private User saveOrUpdate(OAuth2UserInfo userInfo, String registrationId) {
        User.AuthProvider provider = User.AuthProvider.valueOf(registrationId);

        return userRepository.findByProviderAndProviderId(provider, userInfo.getId())
                .map(user -> user.update(userInfo.getName(), userInfo.getImageUrl()))
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .email(userInfo.getEmail())
                                .nickname(userInfo.getName())
                                .profileImageUrl(userInfo.getImageUrl())
                                .provider(provider)
                                .providerId(userInfo.getId())
                                .build()
                ));
    }
}
