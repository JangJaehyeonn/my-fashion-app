package com.fashionapp.domain.auth;

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

    /** 가입 후 바로 로그인 상태가 되도록 토큰을 함께 발급 */
    public Map<String, String> signup(SignupRequest request) {
        LocalAuthRules.requireLoginId(request.getLoginId());
        LocalAuthRules.requirePassword(request.getPassword());
        String nickname = LocalAuthRules.requireNickname(request.getNickname());

        if (userRepository.existsByLoginId(request.getLoginId())) {
            throw new CustomException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        User user;
        try {
            user = userRepository.saveAndFlush(User.createLocal(
                    request.getLoginId(), passwordEncoder.encode(request.getPassword()), nickname));
        } catch (DataIntegrityViolationException e) {
            // 중복 확인과 저장 사이에 같은 아이디로 동시에 가입한 경우 (login_id unique 제약)
            throw new CustomException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        log.info("local signup loginId={} userId={}", user.getLoginId(), user.getId());
        return authTokenService.issueTokens(user.getId());
    }

    /**
     * 아이디·비밀번호 로그인. 아이디가 없든 비밀번호가 틀리든 같은 LOGIN_FAILED(401)로 응답한다.
     * 아이디가 없을 때도 더미 해시와 비교해 BCrypt 한 번만큼 시간을 써서, 응답 시간으로 아이디 존재를 추측하기 어렵게 한다.
     */
    public Map<String, String> login(LoginRequest request) {
        String loginId = request.getLoginId() == null ? "" : request.getLoginId().trim();
        String password = request.getPassword() == null ? "" : request.getPassword();

        User user = userRepository.findByLoginId(loginId).filter(User::isLocal).orElse(null);
        String hash = user != null && user.getPasswordHash() != null ? user.getPasswordHash() : dummyHash();
        boolean matches = passwordEncoder.matches(password, hash);

        if (user == null || !matches) {
            log.info("local login failed loginId={}", loginId);
            throw new CustomException(ErrorCode.LOGIN_FAILED);
        }
        return authTokenService.issueTokens(user.getId());
    }

    private volatile String dummyHash;

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("dummy-password-for-timing-0");
        }
        return dummyHash;
    }

    /** 아이디 사용 가능 여부. 형식이 틀리면 400 */
    public boolean isLoginIdAvailable(String loginId) {
        LocalAuthRules.requireLoginId(loginId);
        return !userRepository.existsByLoginId(loginId);
    }
}
