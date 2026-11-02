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
    INVALID_AUTH_CODE(HttpStatus.UNAUTHORIZED, "로그인 코드가 만료되었거나 이미 사용되었습니다. 다시 로그인해 주세요."),
    INVALID_BODY_PROFILE(HttpStatus.BAD_REQUEST, "유효하지 않은 체형/스타일 값입니다."),

    // 아이디·비밀번호 가입/로그인 — 문구는 앱 입력 칸 아래 안내로 그대로 노출됨
    INVALID_LOGIN_ID(HttpStatus.BAD_REQUEST, "아이디는 영문 소문자와 숫자로 4~20자여야 해요."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호는 영문과 숫자를 포함해 8~64자여야 해요."),
    INVALID_NICKNAME(HttpStatus.BAD_REQUEST, "닉네임은 1~20자로 입력해 주세요."),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 사용 중인 아이디예요."),

    INVALID_IMAGE_FORMAT(HttpStatus.BAD_REQUEST, "이미지 파일만 업로드할 수 있습니다."),
    IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 업로드에 실패했습니다."),
    AI_SERVER_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "AI 서버와 통신에 실패했습니다."),
    AI_SERVER_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI 응답이 늦어지고 있어요. 잠시 후 다시 시도해 주세요."),

    DIAGNOSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "진단 기록을 찾을 수 없습니다."),
    CLOTHES_NOT_FOUND(HttpStatus.NOT_FOUND, "옷을 찾을 수 없습니다."),
    CLOSET_EMPTY(HttpStatus.BAD_REQUEST, "옷장에 등록된 옷이 없습니다.");

    private final HttpStatus status;
    private final String message;
}
