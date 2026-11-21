package com.fashionapp.domain.auth;

import com.fashionapp.domain.consent.ConsentService;
import com.fashionapp.domain.consent.ConsentType;
import com.fashionapp.domain.user.AuthTokenService;
import com.fashionapp.domain.user.User;
import com.fashionapp.domain.user.UserRepository;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.Map;

/**
 * 아이디·비밀번호 가입/로그인. 토큰은 OAuth와 같은 {@link AuthTokenService}로 발급한다.
 * 비밀번호와 해시는 어떤 로그에도 남기지 않는다 (로그에는 아이디·사용자 id만).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalAuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final ConsentService consentService;
    private final Clock clock;

    /** 가입 후 바로 로그인 상태가 되도록 토큰을 함께 발급 */
    public Map<String, String> signup(SignupRequest request) {
        LocalAuthRules.requireLoginId(request.getLoginId());
        LocalAuthRules.requirePassword(request.getPassword());
        String nickname = LocalAuthRules.requireNickname(request.getNickname());
        if (!Boolean.TRUE.equals(request.getAgeOver14())) {
            throw new CustomException(ErrorCode.AGE_REQUIRED_FOR_SIGNUP);
        }

        if (userRepository.existsByLoginId(request.getLoginId())) {
            throw new CustomException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        User user;
        try {
            User newUser = User.createLocal(request.getLoginId(), passwordEncoder.encode(request.getPassword()), nickname);
            newUser.confirmAge(LocalDateTime.now(clock));
            user = userRepository.saveAndFlush(newUser);
        } catch (DataIntegrityViolationException e) {
            // 중복 확인과 저장 사이에 같은 아이디로 동시에 가입한 경우 (login_id unique 제약)
            throw new CustomException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        consentService.ensureAgreed(user.getId(), ConsentType.AGE_OVER_14, user.getAgeConfirmedAt());
        log.info("local signup loginId={} userId={}", user.getLoginId(), user.getId());
        return authTokenService.issueTokens(user.getId());
    }

    /**
     * 아이디·비밀번호 로그인. 아이디가 없든 비밀번호가 틀리든 같은 LOGIN_FAILED(401)로 응답한다.
     * 아이디가 없을 때도 더미 해시와 비교해 BCrypt 한 번만큼 시간을 써서, 응답 시간으로 아이디 존재를 추측하기 어렵게 한다.
     */
    public Map<String, String> login(LoginRequest request) {
        // 아이디는 영문 소문자만 허용되므로 대문자로 입력해도 같은 아이디로 취급 (잠금 키도 같은 값으로)
        String loginId = request.getLoginId() == null ? "" : request.getLoginId().trim().toLowerCase(Locale.ROOT);
        String password = request.getPassword() == null ? "" : request.getPassword();

        // 잠금 중이면 비밀번호가 맞아도 거부 (비밀번호 확인 전에 판단)
        if (loginAttemptLimiter.isLocked(loginId)) {
            throw new CustomException(ErrorCode.LOGIN_LOCKED);
        }

        User user = userRepository.findByLoginId(loginId).filter(User::isLocal).orElse(null);
        String hash = user != null && user.getPasswordHash() != null ? user.getPasswordHash() : dummyHash();
        boolean matches = passwordEncoder.matches(password, hash);

        if (user == null || !matches) {
            boolean lockedNow = loginAttemptLimiter.recordFailure(loginId);
            log.info("local login failed loginId={} locked={}", loginId, lockedNow);
            throw new CustomException(lockedNow ? ErrorCode.LOGIN_LOCKED : ErrorCode.LOGIN_FAILED);
        }
        loginAttemptLimiter.reset(loginId);
        return authTokenService.issueTokens(user.getId());
    }

    private volatile String dummyHash;

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("dummy-password-for-timing-0");
        }
        return dummyHash;
    }

    /**
     * 비밀번호 변경. 현재 비밀번호를 확인한 뒤 바꾸고, 리프레시 토큰을 새로 발급해 기존 것을 모두 무효화한다
     * (Redis의 refresh:{userId}는 사용자당 하나라 덮어쓰면 다른 기기의 리프레시 토큰도 더는 쓸 수 없음).
     * 변경한 기기는 응답의 새 토큰으로 로그인을 유지한다. 다른 기기의 액세스 토큰은 만료(최대 1시간)까지 유효하다.
     *
     * <p>현재 비밀번호 오류는 401이 아니라 400 — 앱의 토큰 자동 갱신(401 처리)이 끼어들지 않게 하기 위함.
     */
    @Transactional
    public Map<String, String> changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!user.isLocal() || user.getPasswordHash() == null) {
            throw new CustomException(ErrorCode.PASSWORD_NOT_SUPPORTED);
        }
        String current = request.getCurrentPassword() == null ? "" : request.getCurrentPassword();
        if (!passwordEncoder.matches(current, user.getPasswordHash())) {
            log.info("password change rejected (wrong current password) userId={}", userId);
            throw new CustomException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }
        LocalAuthRules.requirePassword(request.getNewPassword());
        if (request.getNewPassword().equals(current)) {
            throw new CustomException(ErrorCode.SAME_PASSWORD);
        }

        user.changePasswordHash(passwordEncoder.encode(request.getNewPassword()));
        log.info("password changed userId={}", userId);
        return authTokenService.issueTokens(userId);
    }

    /** 아이디 사용 가능 여부. 형식이 틀리면 400 */
    public boolean isLoginIdAvailable(String loginId) {
        LocalAuthRules.requireLoginId(loginId);
        return !userRepository.existsByLoginId(loginId);
    }
}
