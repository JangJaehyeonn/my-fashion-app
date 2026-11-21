package com.fashionapp.global.config;

import com.fashionapp.domain.user.UserPrincipal;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 만 14세 이상 확인 전인 사용자는 확인·조회·탈퇴·로그아웃 외의 API를 쓸 수 없게 막는다 (403 AGE_CONFIRMATION_REQUIRED).
 * FR-05부터 확인 API는 약관·방침 동의까지 모두 받아야 확인 시각을 저장하므로, 약관 동의 전 소셜 계정도 같은 범위로 막힌다.
 * 아이디 가입은 가입 때 확인하므로 항상 통과하고, 소셜 로그인 첫 가입과 이 기능 이전에 가입한 사용자가 대상이다.
 * 앱 화면만 믿지 않고 서버에서 막기 위함. 인증되지 않은 요청은 여기서 판단하지 않는다(보안 설정이 401로 처리).
 */
public class AgeGateInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (isAllowedBeforeConfirmation(request.getMethod(), request.getRequestURI())) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal && !principal.isAgeConfirmed()) {
            throw new CustomException(ErrorCode.AGE_CONFIRMATION_REQUIRED);
        }
        return true;
    }

    static boolean isAllowedBeforeConfirmation(String method, String path) {
        if (path.startsWith("/api/auth/") || path.equals("/api/users/me/age-confirmation")) {
            return true;
        }
        // 확인 화면이 프로필을 읽고, 만 14세 미만이면 바로 탈퇴할 수 있어야 함
        return path.equals("/api/users/me") && ("GET".equals(method) || "DELETE".equals(method));
    }
}
