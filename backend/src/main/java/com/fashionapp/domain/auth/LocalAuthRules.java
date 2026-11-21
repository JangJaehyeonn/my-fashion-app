package com.fashionapp.domain.auth;

import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;

import java.util.regex.Pattern;

/** 아이디·비밀번호·닉네임 규칙. 앱(SignupViewModel)도 같은 규칙으로 입력 칸 아래 안내를 띄운다 */
public final class LocalAuthRules {

    private static final Pattern LOGIN_ID = Pattern.compile("^[a-z0-9]{4,20}$");
    private static final Pattern HAS_LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern HAS_DIGIT = Pattern.compile("[0-9]");
    // BCrypt는 72바이트 이후를 무시하므로 상한을 둔다
    static final int PASSWORD_MIN = 8;
    static final int PASSWORD_MAX = 64;
    static final int NICKNAME_MAX = 20;

    private LocalAuthRules() {}

    public static boolean isValidLoginId(String loginId) {
        return loginId != null && LOGIN_ID.matcher(loginId).matches();
    }

    public static boolean isValidPassword(String password) {
        return password != null
                && password.length() >= PASSWORD_MIN && password.length() <= PASSWORD_MAX
                && HAS_LETTER.matcher(password).find()
                && HAS_DIGIT.matcher(password).find();
    }

    public static void requireLoginId(String loginId) {
        if (!isValidLoginId(loginId)) throw new CustomException(ErrorCode.INVALID_LOGIN_ID);
    }

    public static void requirePassword(String password) {
        if (!isValidPassword(password)) throw new CustomException(ErrorCode.INVALID_PASSWORD);
    }

    /** 앞뒤 공백을 지운 닉네임 (1~20자가 아니면 예외) */
    public static String requireNickname(String nickname) {
        String trimmed = nickname == null ? "" : nickname.trim();
        if (trimmed.isEmpty() || trimmed.length() > NICKNAME_MAX) throw new CustomException(ErrorCode.INVALID_NICKNAME);
        return trimmed;
    }
}
