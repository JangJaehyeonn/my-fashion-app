package com.fashionapp.domain.user;

import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FR-28: commute times and travel mode in the profile (PUT /api/users/me) */
class CommuteProfileTest {

    private static User user() {
        return User.builder().provider(User.AuthProvider.local).providerId("tester").build();
    }

    private static UpdateProfileRequest request(String json) throws Exception {
        return new ObjectMapper().readValue(json, UpdateProfileRequest.class);
    }

    @Test
    @DisplayName("HH:mm 시각과 이동 수단을 저장하고 응답에 같은 이름·형식으로 준다")
    void savesAndReturns() throws Exception {
        User user = user();

        CommuteProfile.apply(user, request(
                "{\"commuteOutAt\": \"06:00\", \"commuteBackAt\": \"21:00\", \"travelMode\": \"TRANSIT\"}"));

        assertThat(user.getCommuteOutAt()).isEqualTo(LocalTime.of(6, 0));
        assertThat(user.getCommuteBackAt()).isEqualTo(LocalTime.of(21, 0));
        assertThat(user.getTravelMode()).isEqualTo(User.TravelMode.TRANSIT);
        UserResponse response = UserResponse.from(user);
        assertThat(response.getCommuteOutAt()).isEqualTo("06:00");
        assertThat(response.getCommuteBackAt()).isEqualTo("21:00");
        assertThat(response.getTravelMode()).isEqualTo("TRANSIT");
    }

    @Test
    @DisplayName("필드가 없으면(옛 앱) 그대로 두고, 빈 문자열이면 지운다")
    void absentKeepsBlankClears() throws Exception {
        User user = user();
        user.updateCommute(LocalTime.of(7, 30), LocalTime.of(18, 0), User.TravelMode.WALK);

        CommuteProfile.apply(user, request("{\"nickname\": \"닉\"}"));
        assertThat(user.getCommuteOutAt()).isEqualTo(LocalTime.of(7, 30));
        assertThat(user.getTravelMode()).isEqualTo(User.TravelMode.WALK);

        CommuteProfile.apply(user, request("{\"commuteOutAt\": \"\", \"travelMode\": \"\"}"));
        assertThat(user.getCommuteOutAt()).isNull();
        assertThat(user.getCommuteBackAt()).isEqualTo(LocalTime.of(18, 0));
        assertThat(user.getTravelMode()).isNull();
        assertThat(UserResponse.from(user).getCommuteOutAt()).isNull();
    }

    @Test
    @DisplayName("잘못된 시각·이동 수단은 INVALID_COMMUTE_PROFILE(400)")
    void rejectsInvalidValues() {
        for (String json : new String[]{
                "{\"commuteOutAt\": \"25:00\"}", "{\"commuteBackAt\": \"7시\"}", "{\"travelMode\": \"BIKE\"}"}) {
            assertThatThrownBy(() -> CommuteProfile.apply(user(), request(json)))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_COMMUTE_PROFILE);
        }
        assertThat(ErrorCode.INVALID_COMMUTE_PROFILE.getStatus().value()).isEqualTo(400);
    }
}
