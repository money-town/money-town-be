package com.moneykk.moneytown.offering.global.exception;

import com.moneykk.moneytown.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Offering Service의 Outbox 운영 처리 중 발생하는 오류 코드.
 */
public enum OutboxErrorCode implements ErrorCode {

    // 403 FORBIDDEN
    OUTBOX_RETRY_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "OUTBOX_403_01",
            "Outbox 이벤트 재처리 권한이 없습니다."
    ),

    // 404 NOT_FOUND
    OUTBOX_EVENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "OUTBOX_404_01",
            "Outbox 이벤트를 찾을 수 없습니다."
    ),

    // 409 CONFLICT
    OUTBOX_RETRY_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "OUTBOX_409_01",
            "현재 상태에서는 Outbox 이벤트를 재처리할 수 없습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    OutboxErrorCode(
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