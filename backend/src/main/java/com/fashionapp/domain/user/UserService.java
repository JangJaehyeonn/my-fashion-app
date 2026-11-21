package com.fashionapp.domain.user;

import com.fashionapp.domain.consent.ConsentService;
import com.fashionapp.domain.consent.ConsentType;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ConsentService consentService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public UserResponse getMyProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        try {
            User.BodyType bodyType = request.getBodyType() != null
                    ? User.BodyType.valueOf(request.getBodyType()) : null;
            List<User.PreferredStyle> styles = resolvePreferredStyles(request);

            user.update(request.getNickname(), user.getProfileImageUrl(),
                    request.getHeight(), request.getWeight(), bodyType,
                    styles.isEmpty() ? null : styles.get(0));
            user.updatePreferredStyles(styles);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_BODY_PROFILE);
        }

        return UserResponse.from(user);
    }

    // preferredStyles(다중)가 오면 그것을, 없으면 옛 앱의 단일 preferredStyle을 사용. 둘 다 없으면 모두 해제.
    // 잘못된 값이면 IllegalArgumentException → 호출부에서 INVALID_BODY_PROFILE로 변환
    private List<User.PreferredStyle> resolvePreferredStyles(UpdateProfileRequest request) {
        if (request.getPreferredStyles() != null) {
            return request.getPreferredStyles().stream()
                    .map(User.PreferredStyle::valueOf)
                    .distinct()
                    .toList();
        }
        if (request.getPreferredStyle() != null) {
            return List.of(User.PreferredStyle.valueOf(request.getPreferredStyle()));
        }
        return List.of();
    }

    /** 가상 피팅 사진 전송 동의. 이미 동의했으면 처음 동의 시각을 유지한다. 동의 이력(consent_records)도 남긴다 */
    @Transactional
    public UserResponse grantVtonConsent(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!user.hasVtonConsent()) {
            user.grantVtonConsent(LocalDateTime.now(clock));
        }
        consentService.ensureAgreed(userId, ConsentType.VTON, user.getVtonConsentAt());
        return UserResponse.from(user);
    }

    /** 동의 철회 — 이후 피팅 요청은 다시 동의할 때까지 403. 이력 행은 지우지 않고 철회 시각만 기록한다 */
    @Transactional
    public UserResponse revokeVtonConsent(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        user.revokeVtonConsent();
        consentService.revoke(userId, ConsentType.VTON);
        return UserResponse.from(user);
    }

    /** 만 14세 이상 확인 (소셜 첫 가입·기존 사용자). 이미 확인했으면 처음 시각을 유지한다. 미만이면 앱이 탈퇴를 호출한다 */
    @Transactional
    public UserResponse confirmAge(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        user.confirmAge(LocalDateTime.now(clock));
        consentService.ensureAgreed(userId, ConsentType.AGE_OVER_14, user.getAgeConfirmedAt());
        return UserResponse.from(user);
    }
}
