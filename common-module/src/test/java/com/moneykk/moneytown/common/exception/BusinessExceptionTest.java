package com.moneykk.moneytown.common.exception;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class BusinessExceptionTest {

    @Test
    @DisplayName("ErrorCode를 이용하여 BusinessException을 생성한다")
    void createBusinessException() {
        // given
        ErrorCode errorCode = CommonErrorCode.INVALID_INPUT_VALUE;

        // when
        BusinessException exception = new BusinessException(errorCode);

        // then
        assertThat(exception.getErrorCode()).isEqualTo(errorCode);
        assertThat(exception.getMessage()).isEqualTo(errorCode.getMessage());
    }

    @Test
    @DisplayName("원인 예외를 보존하여 BusinessException을 생성한다")
    void createBusinessExceptionWithCause() {
        // given
        ErrorCode errorCode = CommonErrorCode.INTERNAL_SERVER_ERROR;
        RuntimeException cause = new RuntimeException("original cause");

        // when
        BusinessException exception =
                new BusinessException(errorCode, cause);

        // then
        assertThat(exception.getErrorCode()).isEqualTo(errorCode);
        assertThat(exception.getMessage()).isEqualTo(errorCode.getMessage());
        assertThat(exception.getCause()).isSameAs(cause);
    }

}
