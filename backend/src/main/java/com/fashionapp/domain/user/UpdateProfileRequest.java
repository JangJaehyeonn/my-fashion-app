package com.fashionapp.domain.user;

import lombok.Getter;

@Getter
public class UpdateProfileRequest {
    private String nickname;
    private Integer height;
    private Integer weight;
    private String bodyType;
    private String preferredStyle;
}
