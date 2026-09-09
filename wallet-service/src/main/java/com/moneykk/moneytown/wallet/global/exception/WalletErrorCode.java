package com.moneykk.moneytown.wallet.global.exception;

import com.moneykk.moneytown.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum WalletErrorCode implements ErrorCode {
    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "WALLET_404_01", "존재하지 않는 지갑입니다."),
    INSUFFICIENT_AVAILABLE_BALANCE(HttpStatus.BAD_REQUEST, "WALLET_400_01", "가용잔액이 부족합니다."),
    INVALID_HOLD_STATUS_TRANSITION(HttpStatus.CONFLICT, "WALLET_409_02", "허용되지 않는 동결 상태 전이입니다."),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "WALLET_400_02", "금액은 0보다 커야 합니다."),
    INSUFFICIENT_HOLD_BALANCE(HttpStatus.BAD_REQUEST, "WALLET_400_03", "동결 잔액이 부족합니다."),
    INVALID_TRANSACTION_BALANCE_SNAPSHOT(HttpStatus.BAD_REQUEST, "WALLET_400_04", "거래 유형과 잔액 스냅샷이 일치하지 않습니다."),
    BALANCE_OVERFLOW(HttpStatus.BAD_REQUEST, "WALLET_400_05", "처리 가능한 최대 금액을 초과했습니다."),
    INELIGIBLE_FOR_TRANSACTION(HttpStatus.FORBIDDEN, "WALLET_403_01", "입출금 가능한 계정 상태가 아닙니다."),
    WALLET_ADMIN_ACCESS_DENIED(HttpStatus.FORBIDDEN, "WALLET_403_02", "지갑 상세 조회는 ADMIN 권한으로만 이용할 수 있습니다."),
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "WALLET_409_01", "동일한 멱등키로 다른 요청이 이미 처리되었습니다.");

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
