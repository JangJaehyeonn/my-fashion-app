package com.fashionapp.domain.user;

import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.function.Function;

/**
 * FR-28 commute profile update rules. Each field: null (absent) keeps the stored value, "" clears it, otherwise it is
 * parsed ("HH:mm", WALK/TRANSIT/CAR). Older apps that do not send these fields therefore never wipe them.
 */
final class CommuteProfile {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private CommuteProfile() {
    }

    static void apply(User user, UpdateProfileRequest request) {
        LocalTime out = resolve(request.getCommuteOutAt(), user.getCommuteOutAt(), CommuteProfile::parseTime);
        LocalTime back = resolve(request.getCommuteBackAt(), user.getCommuteBackAt(), CommuteProfile::parseTime);
        User.TravelMode mode = resolve(request.getTravelMode(), user.getTravelMode(), CommuteProfile::parseMode);
        user.updateCommute(out, back, mode);
    }

    private static <T> T resolve(String raw, T current, Function<String, T> parser) {
        if (raw == null) {
            return current;
        }
        if (raw.isBlank()) {
            return null;
        }
        return parser.apply(raw.trim());
    }

    static LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value, HH_MM);
        } catch (DateTimeParseException e) {
            throw new CustomException(ErrorCode.INVALID_COMMUTE_PROFILE);
        }
    }

    static User.TravelMode parseMode(String value) {
        try {
            return User.TravelMode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_COMMUTE_PROFILE);
        }
    }
}
