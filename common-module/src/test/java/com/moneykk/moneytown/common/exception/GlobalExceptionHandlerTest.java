package com.moneykk.moneytown.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.moneykk.moneytown.common.response.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;
    private Logger logger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest(
                "POST",
                "/api/v1/test"
        );

        logger = (Logger) LoggerFactory.getLogger(
                GlobalExceptionHandler.class
        );

        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logAppender);
        logAppender.stop();
    }

    @Test
    @DisplayName("500 BusinessException은 ERROR와 스택 트레이스를 기록한다")
    void logsInternalBusinessExceptionAsError() {
        // given
        BusinessException exception =
                new BusinessException(TestErrorCode.INTERNAL_ERROR);

        // when
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBusinessException(exception, request);

        // then
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(response.getBody())
                .isEqualTo(ApiResponse.error(
                        TestErrorCode.INTERNAL_ERROR.getCode(),
                        TestErrorCode.INTERNAL_ERROR.getMessage()
                ));

        assertThat(logAppender.list)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getLevel())
                            .isEqualTo(Level.ERROR);

                    assertThat(event.getFormattedMessage())
                            .contains(
                                    "method=POST",
                                    "uri=/api/v1/test",
                                    "status=500",
                                    "code=TEST_500"
                            );

                    assertThat(event.getThrowableProxy())
                            .isNotNull();
                });
    }

    @Test
    @DisplayName("503 BusinessException은 WARN으로 기록하고 기존 응답을 유지한다")
    void logsUnavailableBusinessExceptionAsWarn() {
        // given
        BusinessException exception =
                new BusinessException(TestErrorCode.SERVICE_UNAVAILABLE);

        // when
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBusinessException(exception, request);

        // then
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        assertThat(response.getBody())
                .isEqualTo(ApiResponse.error(
                        TestErrorCode.SERVICE_UNAVAILABLE.getCode(),
                        TestErrorCode.SERVICE_UNAVAILABLE.getMessage()
                ));

        assertThat(logAppender.list)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getLevel())
                            .isEqualTo(Level.WARN);

                    assertThat(event.getFormattedMessage())
                            .contains(
                                    "method=POST",
                                    "uri=/api/v1/test",
                                    "status=503",
                                    "code=TEST_503"
                            );

                    assertThat(event.getThrowableProxy())
                            .isNull();
                });
    }

    @Test
    @DisplayName("Spring ErrorResponse 500은 내부 detail을 숨기고 ERROR로 기록한다")
    void hidesInternalErrorResponseDetail() {
        // given
        ResponseStatusException exception =
                new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "노출되면 안 되는 내부 메시지"
                );

        // when
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnexpectedException(exception, request);

        // then
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(response.getBody())
                .isEqualTo(ApiResponse.error(
                        CommonErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                        CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage()
                ));

        assertThat(response.getBody().message())
                .doesNotContain("노출되면 안 되는 내부 메시지");

        assertThat(logAppender.list)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getLevel())
                            .isEqualTo(Level.ERROR);

                    assertThat(event.getFormattedMessage())
                            .contains(
                                    "method=POST",
                                    "uri=/api/v1/test",
                                    "status=500",
                                    "code=COMMON_500"
                            );

                    assertThat(event.getThrowableProxy())
                            .isNotNull();
                });
    }

    @Test
    @DisplayName("Spring ErrorResponse 503은 내부 detail을 숨기고 WARN으로 기록한다")
    void hidesUnavailableErrorResponseDetail() {
        // given
        ResponseStatusException exception =
                new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "외부 서비스 내부 응답"
                );

        // when
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnexpectedException(exception, request);

        // then
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        assertThat(response.getBody())
                .isEqualTo(ApiResponse.error(
                        "COMMON_503",
                        "요청을 처리할 수 없습니다."
                ));

        assertThat(response.getBody().message())
                .doesNotContain("외부 서비스 내부 응답");

        assertThat(logAppender.list)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getLevel())
                            .isEqualTo(Level.WARN);

                    assertThat(event.getFormattedMessage())
                            .contains(
                                    "method=POST",
                                    "uri=/api/v1/test",
                                    "status=503",
                                    "code=COMMON_503"
                            );

                    assertThat(event.getThrowableProxy())
                            .isNull();
                });
    }

    @Test
    @DisplayName("Spring ErrorResponse 4xx는 기존 detail을 응답으로 반환한다")
    void preservesClientErrorResponseDetail() {
        // given
        ResponseStatusException exception =
                new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "잘못된 요청입니다."
                );

        // when
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnexpectedException(exception, request);

        // then
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(response.getBody())
                .isEqualTo(ApiResponse.error(
                        "COMMON_400",
                        "잘못된 요청입니다."
                ));

        assertThat(logAppender.list)
                .isEmpty();
    }

    @Test
    @DisplayName("예상하지 못한 예외는 COMMON_500 응답과 스택 트레이스를 기록한다")
    void handlesUnexpectedException() {
        // given
        RuntimeException exception =
                new RuntimeException("unexpected error");

        // when
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnexpectedException(exception, request);

        // then
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(response.getBody())
                .isEqualTo(ApiResponse.error(
                        CommonErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                        CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage()
                ));

        assertThat(logAppender.list)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getLevel())
                            .isEqualTo(Level.ERROR);

                    assertThat(event.getFormattedMessage())
                            .contains(
                                    "method=POST",
                                    "uri=/api/v1/test"
                            );

                    assertThat(event.getThrowableProxy())
                            .isNotNull();
                });
    }

    private enum TestErrorCode implements ErrorCode {

        INTERNAL_ERROR(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "TEST_500",
                "내부 상태 오류"
        ),
        SERVICE_UNAVAILABLE(
                HttpStatus.SERVICE_UNAVAILABLE,
                "TEST_503",
                "외부 서비스를 사용할 수 없습니다."
        );

        private final HttpStatus status;
        private final String code;
        private final String message;

        TestErrorCode(
                HttpStatus status,
                String code,
                String message
        ) {
            this.status = status;
            this.code = code;
            this.message = message;
        }

        @Override
        public HttpStatus getStatus() {
            return status;
        }

        @Override
        public String getCode() {
            return code;
        }

        @Override
        public String getMessage() {
            return message;
        }
    }
}