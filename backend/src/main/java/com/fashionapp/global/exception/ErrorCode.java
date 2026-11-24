package com.fashionapp.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    API_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 주소를 찾을 수 없어요."),
    UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 로그인 제공자입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다."),
    INVALID_AUTH_CODE(HttpStatus.UNAUTHORIZED, "로그인 코드가 만료되었거나 이미 사용되었습니다. 다시 로그인해 주세요."),
    INVALID_BODY_PROFILE(HttpStatus.BAD_REQUEST, "유효하지 않은 체형/스타일 값입니다."),
    INVALID_COMMUTE_PROFILE(HttpStatus.BAD_REQUEST, "출퇴근 시각(00:00~23:59)이나 이동 수단 값이 올바르지 않아요."),

    // 아이디·비밀번호 가입/로그인 — 문구는 앱 입력 칸 아래 안내로 그대로 노출됨
    INVALID_LOGIN_ID(HttpStatus.BAD_REQUEST, "아이디는 영문 소문자와 숫자로 4~20자여야 해요."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호는 영문과 숫자를 포함해 8~64자여야 해요."),
    INVALID_NICKNAME(HttpStatus.BAD_REQUEST, "닉네임은 1~20자로 입력해 주세요."),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 사용 중인 아이디예요."),
    // 아이디가 없을 때와 비밀번호가 틀렸을 때 같은 문구·상태 — 아이디 존재 여부를 알려주지 않기 위함
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 틀렸어요"),
    INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "현재 비밀번호가 틀렸어요."),
    SAME_PASSWORD(HttpStatus.BAD_REQUEST, "지금과 다른 비밀번호를 입력해 주세요."),
    PASSWORD_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "소셜 로그인 계정은 비밀번호를 바꿀 수 없어요."),
    LOGIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "로그인에 여러 번 실패해 잠시 잠겼어요. 5분 후 다시 시도해 주세요."),

    INVALID_IMAGE_FORMAT(HttpStatus.BAD_REQUEST, "이미지 파일만 업로드할 수 있습니다."),
    IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 업로드에 실패했습니다."),
    AI_SERVER_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "AI 서버와 통신에 실패했습니다."),
    AI_SERVER_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI 응답이 늦어지고 있어요. 잠시 후 다시 시도해 주세요."),

    WEATHER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "날씨 정보를 불러오지 못했어요."),
    INVALID_COORDINATES(HttpStatus.BAD_REQUEST, "위치 좌표가 올바르지 않아요."),

    // 만 14세 미만은 가입 불가 — 가입 시 확인 체크 필수, 소셜·기존 사용자는 확인 전까지 대부분의 API 차단
    AGE_REQUIRED_FOR_SIGNUP(HttpStatus.BAD_REQUEST, "만 14세 이상만 가입할 수 있어요."),
    // 이용약관·개인정보처리방침 동의 필수 (가입, 소셜 첫 로그인 확인)
    TERMS_AGREEMENT_REQUIRED(HttpStatus.BAD_REQUEST, "이용약관과 개인정보처리방침에 동의해 주세요."),
    AGE_CONFIRMATION_REQUIRED(HttpStatus.FORBIDDEN, "만 14세 이상인지 확인이 필요해요."),
    VTON_CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "가상 피팅을 쓰려면 사진 전송에 동의해 주세요."),

    DIAGNOSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "진단 기록을 찾을 수 없습니다."),
    CLOTHES_NOT_FOUND(HttpStatus.NOT_FOUND, "옷을 찾을 수 없습니다."),
    INVALID_CLOTHES_UPDATE(HttpStatus.BAD_REQUEST, "카테고리는 상의·하의·아우터·신발·기타, 계절은 봄·여름·가을·겨울 중에서 고르고, 색상·이름·핏·소재·패턴은 1~50자로 입력해 주세요."),
    CLOTHES_IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "옷 사진을 찾을 수 없어 다시 분류할 수 없어요."),
    CLOTHES_RECLASSIFY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "지금은 AI 분류를 할 수 없어요. 잠시 후 다시 시도하거나 직접 입력해 주세요."),
    INVALID_WEAR_REQUEST(HttpStatus.BAD_REQUEST, "입은 옷을 하나 이상 고르고, 날짜는 오늘이나 지난 날짜로 골라 주세요."),
    INVALID_WEAR_CLOTHES(HttpStatus.BAD_REQUEST, "내 옷장에 없는 옷이 있어 착용 기록을 남기지 않았어요."),
    // FR-11 살까 말까
    CLOSET_TOO_SMALL(HttpStatus.BAD_REQUEST, "옷장에 5벌 이상 있어야 비교할 수 있어요."),
    INVALID_PURCHASE_PRICE(HttpStatus.BAD_REQUEST, "가격은 0원 이상 숫자로 입력해 주세요."),
    PURCHASE_CHECK_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "지금은 옷을 분석할 수 없어요. 잠시 후 다시 시도해 주세요."),
    // FR-12 살까 말까 결정·기록
    PURCHASE_CHECK_NOT_FOUND(HttpStatus.NOT_FOUND, "판정 기록을 찾을 수 없어요."),
    PURCHASE_CHECK_EXPIRED(HttpStatus.GONE, "판정한 지 30분이 지나 사진이 지워졌어요. 다시 찍어 판정해 주세요."),
    PURCHASE_ALREADY_DECIDED(HttpStatus.CONFLICT, "이미 결정한 판정이에요."),
    INVALID_PURCHASE_DECISION(HttpStatus.BAD_REQUEST, "결정은 BUY 또는 SKIP 중에서 골라 주세요."),
    // FR-13 속성 수정 후 재판정
    INVALID_PURCHASE_ATTRIBUTES(HttpStatus.BAD_REQUEST, "카테고리는 상의·하의·아우터·신발·기타, 계절은 봄·여름·가을·겨울 중에서 고르고, 색상·이름·핏·소재·패턴은 1~50자로 입력해 주세요."),
    PURCHASE_CHECK_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "살까 말까는 하루 30번까지 쓸 수 있어요. 내일 다시 시도해 주세요."),
    // FR-27 옷 상태(세탁 중)
    INVALID_CLOTHES_STATUS_REQUEST(HttpStatus.BAD_REQUEST, "상태를 바꿀 옷을 하나 이상 고르고, 상태는 AVAILABLE 또는 LAUNDRY 중에서 골라 주세요."),
    INVALID_STATUS_CLOTHES(HttpStatus.BAD_REQUEST, "내 옷장에 없는 옷이 있어 상태를 바꾸지 않았어요."),
    CLOSET_NONE_AVAILABLE(HttpStatus.BAD_REQUEST, "지금 입을 수 있는 옷이 없어요. 세탁이 끝난 옷은 상태를 바꿔 주세요."),
    CLOSET_EMPTY(HttpStatus.BAD_REQUEST, "옷장에 등록된 옷이 없습니다."),
    CLOSET_ALL_UNCLASSIFIED(HttpStatus.BAD_REQUEST, "분류된 옷이 없어 코디를 추천할 수 없어요. 미분류 옷의 종류를 먼저 정해 주세요.");

    private final HttpStatus status;
    private final String message;
}
