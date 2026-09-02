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

}
