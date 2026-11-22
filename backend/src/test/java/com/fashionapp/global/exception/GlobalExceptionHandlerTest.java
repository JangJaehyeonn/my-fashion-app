package com.fashionapp.global.exception;

import com.fashionapp.global.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-11: error responses carry the ErrorCode name in `code`; success responses have no `code` key. */
class GlobalExceptionHandlerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("CustomException 응답에 code가 ErrorCode 이름으로 들어간다")
    void errorHasCode() throws Exception {
        ResponseEntity<ApiResponse<Void>> res = new GlobalExceptionHandler()
                .handleCustomException(new CustomException(ErrorCode.CLOSET_TOO_SMALL));

        assertThat(res.getStatusCode().value()).isEqualTo(400);
        String json = mapper.writeValueAsString(res.getBody());
        assertThat(mapper.readTree(json).get("code").asText()).isEqualTo("CLOSET_TOO_SMALL");
        assertThat(mapper.readTree(json).get("success").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("성공 응답에는 code 키가 없다")
    void successHasNoCode() throws Exception {
        String json = mapper.writeValueAsString(ApiResponse.success("ok"));
        assertThat(mapper.readTree(json).has("code")).isFalse();
    }
}
