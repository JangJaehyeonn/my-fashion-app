package com.fashionapp.domain.user;

import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * 선호 스타일 다중 선택 + 기존 단일 컬럼(preferred_style) 호환.
 * 운영 DB의 기존 사용자는 preferred_styles가 NULL이므로, 폴백/동기화 규칙이 데이터를 잃지 않는지가 핵심.
 */
@ExtendWith(MockitoExtension.class)
class UserPreferredStylesTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User legacyUser(User.PreferredStyle legacy) {
        return User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .nickname("테스터")
                .provider(User.AuthProvider.google)
                .providerId("g-1")
                .preferredStyle(legacy)   // preferredStyles는 null (이전 전 기존 사용자)
                .build();
    }

    private UpdateProfileRequest request(String json) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, UpdateProfileRequest.class);
    }

    private String rawStyles(User user) throws Exception {
        Field f = User.class.getDeclaredField("preferredStyles");
        f.setAccessible(true);
        return (String) f.get(user);
    }

    @Test
    @DisplayName("이전 전 사용자(preferred_styles NULL)는 기존 단일 값으로 폴백")
    void legacyUserFallsBackToSingleValue() {
        User user = legacyUser(User.PreferredStyle.CASUAL);

        assertThat(user.getPreferredStyleList()).containsExactly(User.PreferredStyle.CASUAL);
        assertThat(UserResponse.from(user).getPreferredStyles()).containsExactly("CASUAL");
        assertThat(UserResponse.from(user).getPreferredStyle()).isEqualTo("CASUAL");
    }

    @Test
    @DisplayName("스타일을 한 번도 안 고른 사용자는 빈 목록")
    void noStyleIsEmpty() {
        User user = legacyUser(null);

        assertThat(user.getPreferredStyleList()).isEmpty();
        assertThat(UserResponse.from(user).getPreferredStyle()).isNull();
    }

    @Test
    @DisplayName("다중 선택 저장 시 레거시 컬럼에는 첫 값이 함께 기록된다 (옛 앱·롤백 호환)")
    void multiSelectWritesFirstToLegacyColumn() throws Exception {
        User user = legacyUser(User.PreferredStyle.CASUAL);
        UUID id = user.getId();
        given(userRepository.findById(id)).willReturn(Optional.of(user));

        UserResponse response = userService.updateProfile(id,
                request("{\"preferredStyles\":[\"MINIMAL\",\"STREET\",\"MINIMAL\"]}"));

        assertThat(response.getPreferredStyles()).containsExactly("MINIMAL", "STREET"); // 중복 제거, 순서 유지
        assertThat(response.getPreferredStyle()).isEqualTo("MINIMAL");
        assertThat(user.getPreferredStyle()).isEqualTo(User.PreferredStyle.MINIMAL);
        assertThat(rawStyles(user)).isEqualTo("MINIMAL,STREET");
    }

    @Test
    @DisplayName("옛 앱이 단일 preferredStyle만 보내도 동작")
    void legacyClientSingleValueStillWorks() {
        User user = legacyUser(null);
        UUID id = user.getId();
        given(userRepository.findById(id)).willReturn(Optional.of(user));

        UserResponse response = userService.updateProfile(id, legacyRequest("FORMAL"));

        assertThat(response.getPreferredStyles()).containsExactly("FORMAL");
        assertThat(response.getPreferredStyle()).isEqualTo("FORMAL");
    }

    private UpdateProfileRequest legacyRequest(String style) {
        try {
            return request("{\"preferredStyle\":\"" + style + "\"}");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("빈 목록을 보내면 모두 해제되고, NULL(미이전)과 구분된다 (기존 단일 값으로 되살아나지 않음)")
    void emptyListClearsAndDoesNotFallBackToLegacy() throws Exception {
        User user = legacyUser(User.PreferredStyle.CASUAL);
        UUID id = user.getId();
        given(userRepository.findById(id)).willReturn(Optional.of(user));

        UserResponse response = userService.updateProfile(id, request("{\"preferredStyles\":[]}"));

        assertThat(response.getPreferredStyles()).isEmpty();
        assertThat(response.getPreferredStyle()).isNull();
        assertThat(rawStyles(user)).isEmpty();
        assertThat(user.getPreferredStyleList()).isEmpty();
    }

    @Test
    @DisplayName("잘못된 스타일 값은 INVALID_BODY_PROFILE")
    void invalidStyleIsRejected() {
        User user = legacyUser(null);
        UUID id = user.getId();
        given(userRepository.findById(id)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateProfile(id, request("{\"preferredStyles\":[\"CASUAL\",\"NOPE\"]}")))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_BODY_PROFILE);
    }

    @Test
    @DisplayName("로그인(OAuth) 시 user.update(...)로 기존 값을 되넣어도 다중 선택이 지워지지 않는다")
    void oauthLoginRefreshKeepsMultiSelect() {
        User user = legacyUser(null);
        user.updatePreferredStyles(List.of(User.PreferredStyle.VINTAGE, User.PreferredStyle.SPORTY));

        // CustomOAuth2UserService가 로그인마다 호출하는 방식 그대로
        user.update("새 닉네임", "http://img", user.getHeight(), user.getWeight(), user.getBodyType(), user.getPreferredStyle());

        assertThat(user.getPreferredStyleList())
                .containsExactly(User.PreferredStyle.VINTAGE, User.PreferredStyle.SPORTY);
    }

    @Test
    @DisplayName("저장된 문자열에 사라진 enum 값이 섞여 있어도 나머지만 읽는다")
    void unknownStoredTokenIsIgnored() throws Exception {
        User user = legacyUser(null);
        Field f = User.class.getDeclaredField("preferredStyles");
        f.setAccessible(true);
        f.set(user, "CASUAL,REMOVED_STYLE, MINIMAL");

        assertThat(user.getPreferredStyleList())
                .containsExactly(User.PreferredStyle.CASUAL, User.PreferredStyle.MINIMAL);
    }
}
