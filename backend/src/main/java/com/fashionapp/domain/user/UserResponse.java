package com.fashionapp.domain.user;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class UserResponse {

    private UUID id;
    private String email;
    private String nickname;
    private String profileImageUrl;
    private String provider;
    private Integer height;
    private Integer weight;
    private String bodyType;
    // 옛 앱 호환용(선택한 스타일 중 첫 번째). 새 앱은 preferredStyles를 사용
    private String preferredStyle;
    private List<String> preferredStyles;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .profileImageUrl(user.getProfileImageUrl())
                .provider(user.getProvider().name())
                .height(user.getHeight())
                .weight(user.getWeight())
                .bodyType(user.getBodyType() != null ? user.getBodyType().name() : null)
                .preferredStyle(user.getPreferredStyleNames().stream().findFirst().orElse(null))
                .preferredStyles(user.getPreferredStyleNames())
                .build();
    }
}
