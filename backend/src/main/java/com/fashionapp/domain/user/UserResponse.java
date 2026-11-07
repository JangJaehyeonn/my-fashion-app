package com.fashionapp.domain.user;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class UserResponse {

    private UUID id;
    private String email;
    private String nickname;
    private String profileImageUrl;
    // google / kakao / local — 앱이 local일 때만 비밀번호 변경 메뉴를 보여줌
    private String provider;
    // 아이디·비밀번호 가입 사용자만 값이 있음
    private String loginId;
    private Integer height;
    private Integer weight;
    private String bodyType;
    // 옛 앱 호환용(선택한 스타일 중 첫 번째). 새 앱은 preferredStyles를 사용
    private String preferredStyle;
    private List<String> preferredStyles;
    // 만 14세 이상 확인 시각 (null = 미확인 → 앱이 확인 화면을 띄움)
    private LocalDateTime ageConfirmedAt;
    // 가상 피팅 사진 전송 동의 시각 (null = 미동의)
    private LocalDateTime vtonConsentAt;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .profileImageUrl(user.getProfileImageUrl())
                .provider(user.getProvider().name())
                .loginId(user.getLoginId())
                .height(user.getHeight())
                .weight(user.getWeight())
                .bodyType(user.getBodyType() != null ? user.getBodyType().name() : null)
                .preferredStyle(user.getPreferredStyleNames().stream().findFirst().orElse(null))
                .preferredStyles(user.getPreferredStyleNames())
                .ageConfirmedAt(user.getAgeConfirmedAt())
                .vtonConsentAt(user.getVtonConsentAt())
                .build();
    }
}
