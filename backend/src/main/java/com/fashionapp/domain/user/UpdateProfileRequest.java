package com.fashionapp.domain.user;

import lombok.Getter;

import java.util.List;

@Getter
public class UpdateProfileRequest {
    private String nickname;
    private Integer height;
    private Integer weight;
    private String bodyType;
    // 옛 앱(단일 선택)용. preferredStyles가 있으면 그쪽이 우선
    private String preferredStyle;
    private List<String> preferredStyles;
}
