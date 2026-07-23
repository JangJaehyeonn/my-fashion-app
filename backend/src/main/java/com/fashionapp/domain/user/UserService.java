package com.fashionapp.domain.user;

import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            User.PreferredStyle preferredStyle = request.getPreferredStyle() != null
                    ? User.PreferredStyle.valueOf(request.getPreferredStyle()) : null;

            user.update(request.getNickname(), user.getProfileImageUrl(),
                    request.getHeight(), request.getWeight(), bodyType, preferredStyle);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_BODY_PROFILE);
        }

        return UserResponse.from(user);
    }
}
