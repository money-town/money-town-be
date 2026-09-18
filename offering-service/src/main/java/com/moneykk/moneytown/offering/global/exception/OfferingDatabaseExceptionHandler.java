package com.moneykk.moneytown.offering.global.exception;

import com.moneykk.moneytown.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class OfferingDatabaseExceptionHandler {

    @ExceptionHandler({
            CannotCreateTransactionException.class,
            DataAccessResourceFailureException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleDatabaseUnavailable(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        OfferingErrorCode errorCode =
                OfferingErrorCode.DATABASE_UNAVAILABLE;

        log.warn(
                "Offering DB 연결 획득 실패. "
                        + "method={}, uri={}, status={}, code={}, exception={}",
                request.getMethod(),
                request.getRequestURI(),
                errorCode.getStatus().value(),
                errorCode.getCode(),
                exception.getClass().getSimpleName()
        );

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(
                        ApiResponse.error(
                                errorCode.getCode(),
                                errorCode.getMessage()
                        )
                );
    }
}