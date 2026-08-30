package com.moneykk.moneytown.common.exception;

import com.moneykk.moneytown.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

// gateway-service는 WebFlux라 별도의 리액티브 핸들러를 자체적으로 갖고 있어 여기서 다루지 않는다.
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        return response(exception.getErrorCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return response(CommonErrorCode.INVALID_INPUT_VALUE.getStatus(), CommonErrorCode.INVALID_INPUT_VALUE.getCode(), message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatchException(MethodArgumentTypeMismatchException exception) {
        String message = String.format("'%s' 파라미터의 값이 올바른 형식이 아닙니다.", exception.getName());
        return response(CommonErrorCode.INVALID_INPUT_VALUE.getStatus(), CommonErrorCode.INVALID_INPUT_VALUE.getCode(), message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadableException(HttpMessageNotReadableException exception) {
        return response(CommonErrorCode.UNREADABLE_REQUEST_BODY);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));
        return response(CommonErrorCode.INVALID_INPUT_VALUE.getStatus(), CommonErrorCode.INVALID_INPUT_VALUE.getCode(), message);
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiResponse<Void>> handlePropertyReferenceException(PropertyReferenceException exception) {
        log.warn("Invalid sort/query property: {}", exception.getMessage());
        return response(CommonErrorCode.INVALID_SORT_PROPERTY);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        if (exception instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            String detail = errorResponse.getBody().getDetail();
            return response(status, "COMMON_" + status.value(), detail != null ? detail : "요청을 처리할 수 없습니다.");
        }
        log.error("Unhandled exception", exception);
        return response(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ApiResponse<Void>> response(ErrorCode errorCode) {
        return response(errorCode.getStatus(), errorCode.getCode(), errorCode.getMessage());
    }

    private ResponseEntity<ApiResponse<Void>> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(code, message));
    }
}