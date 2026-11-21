package com.fashionapp.domain.consent;

import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FR-05 가입 필수 동의: 약관·방침·만 14세가 모두 true여야 통과, 없거나 false면 400 */
class RequiredAgreementsTest {

    @Test
    @DisplayName("셋 다 true면 통과")
    void allAgreedPasses() {
        assertThatCode(() -> new RequiredAgreements(true, true, true).requireAll()).doesNotThrowAnyException();
    }

    static Stream<Arguments> missing() {
        return Stream.of(
                Arguments.of(null, true, true, ErrorCode.TERMS_AGREEMENT_REQUIRED),
                Arguments.of(false, true, true, ErrorCode.TERMS_AGREEMENT_REQUIRED),
                Arguments.of(true, null, true, ErrorCode.TERMS_AGREEMENT_REQUIRED),
                Arguments.of(true, false, true, ErrorCode.TERMS_AGREEMENT_REQUIRED),
                Arguments.of(true, true, null, ErrorCode.AGE_REQUIRED_FOR_SIGNUP),
                Arguments.of(true, true, false, ErrorCode.AGE_REQUIRED_FOR_SIGNUP),
                Arguments.of(null, null, null, ErrorCode.AGE_REQUIRED_FOR_SIGNUP));
    }

    @ParameterizedTest(name = "terms={0} privacy={1} age={2} → {3}")
    @MethodSource("missing")
    void anyMissingIsRejected(Boolean terms, Boolean privacy, Boolean age, ErrorCode expected) {
        assertThatThrownBy(() -> new RequiredAgreements(terms, privacy, age).requireAll())
                .isInstanceOf(CustomException.class)
                .satisfies(e -> {
                    ErrorCode code = ((CustomException) e).getErrorCode();
                    assertThat(code).isEqualTo(expected);
                    assertThat(code.getStatus().value()).isEqualTo(400);
                });
    }

    @Test
    @DisplayName("NONE(본문 없음)은 거절")
    void noneIsRejected() {
        assertThatThrownBy(RequiredAgreements.NONE::requireAll).isInstanceOf(CustomException.class);
    }
}
