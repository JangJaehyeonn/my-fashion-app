package com.fashionapp.domain.user;

import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

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
}
