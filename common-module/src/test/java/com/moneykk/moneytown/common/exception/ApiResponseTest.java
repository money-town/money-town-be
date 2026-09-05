package com.moneykk.moneytown.common.exception;
import com.moneykk.moneytown.common.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {
    @Test
    @DisplayName("성공 응답을 생성한다")
    void createSuccessResponse() {
        // given
        String data = "응답 데이터";
        String message = "요청에 성공했습니다.";

        // when
        ApiResponse<String> response = ApiResponse.success(data, message);

        // then
        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo(data);
        assertThat(response.message()).isEqualTo(message);
        assertThat(response.code()).isNull();
    }

    @Test
    @DisplayName("에러 응답을 생성한다")
    void createErrorResponse() {
        // given
        String code = "COMMON_400";
        String message = "요청 값이 올바르지 않습니다.";

        // when
        ApiResponse<Void> response = ApiResponse.error(code, message);

        // then
        assertThat(response.success()).isFalse();
        assertThat(response.data()).isNull();
        assertThat(response.message()).isEqualTo(message);
        assertThat(response.code()).isEqualTo(code);
    }
}
