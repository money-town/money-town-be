package com.moneykk.moneytown.offering.global.exception;

import com.moneykk.moneytown.common.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.CannotCreateTransactionException;

import static org.assertj.core.api.Assertions.assertThat;

class OfferingDatabaseExceptionHandlerTest {

    private OfferingDatabaseExceptionHandler exceptionHandler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new OfferingDatabaseExceptionHandler();

        request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI(
                "/api/v1/internal/offerings/ai-portfolio-candidates"
        );
    }

    @Test
    @DisplayName("DB 커넥션 획득 실패를 503 응답으로 변환한다")
    void handlesDataAccessResourceFailureException() {
        DataAccessResourceFailureException exception =
                new DataAccessResourceFailureException(
                        "Connection is not available"
                );

        ResponseEntity<ApiResponse<Void>> response =
                exceptionHandler.handleDatabaseUnavailable(
                        exception,
                        request
                );

        assertDatabaseUnavailable(response);
    }

    @Test
    @DisplayName("트랜잭션 시작 실패를 503 응답으로 변환한다")
    void handlesCannotCreateTransactionException() {
        CannotCreateTransactionException exception =
                new CannotCreateTransactionException(
                        "Could not open JPA EntityManager for transaction"
                );

        ResponseEntity<ApiResponse<Void>> response =
                exceptionHandler.handleDatabaseUnavailable(
                        exception,
                        request
                );

        assertDatabaseUnavailable(response);
    }

    private void assertDatabaseUnavailable(
            ResponseEntity<ApiResponse<Void>> response
    ) {
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().data()).isNull();
        assertThat(response.getBody().code())
                .isEqualTo("OFFERING_503_03");
        assertThat(response.getBody().message())
                .isEqualTo(
                        "현재 공모 데이터베이스 연결을 사용할 수 없습니다."
                );
    }
}