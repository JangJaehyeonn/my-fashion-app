package com.fashionapp.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 로그인 제공자입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다."),
    INVALID_BODY_PROFILE(HttpStatus.BAD_REQUEST, "유효하지 않은 체형/스타일 값입니다."),

    CLOTHES_NOT_FOUND(HttpStatus.NOT_FOUND, "옷을 찾을 수 없습니다."),
    INVALID_IMAGE_FORMAT(HttpStatus.BAD_REQUEST, "이미지 파일만 업로드할 수 있습니다."),
    IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 업로드에 실패했습니다."),
    AI_SERVER_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "AI 서버와 통신에 실패했습니다."),

    OUTFIT_NOT_FOUND(HttpStatus.NOT_FOUND, "코디를 찾을 수 없습니다."),
    CALENDAR_NOT_FOUND(HttpStatus.NOT_FOUND, "캘린더 기록을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
