package com.fashionapp.global.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

@Getter
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final String message;
    // Error code name (ErrorCode) so clients can branch without parsing the message. Omitted when null.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String code;

    private ApiResponse(boolean success, T data, String message, String code) {
        this.success = success;
        this.data = data;
        this.message = message;
        this.code = code;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(true, null, null, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message, null);
    }

    public static <T> ApiResponse<T> error(String message, String code) {
        return new ApiResponse<>(false, null, message, code);
    }
}
