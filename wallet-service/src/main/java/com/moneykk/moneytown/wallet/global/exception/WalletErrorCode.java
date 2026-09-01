package com.moneykk.moneytown.wallet.global.exception;

import com.moneykk.moneytown.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum WalletErrorCode implements ErrorCode {
    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "W404", "존재하지 않는 지갑입니다."),
    INSUFFICIENT_AVAILABLE_BALANCE(HttpStatus.BAD_REQUEST, "W001", "가용잔액이 부족합니다."),
    INVALID_HOLD_STATUS_TRANSITION(HttpStatus.CONFLICT, "W002", "허용되지 않는 동결 상태 전이입니다."),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "W003", "금액은 0보다 커야 합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    WalletErrorCode(HttpStatus status, String code, String message) {
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
