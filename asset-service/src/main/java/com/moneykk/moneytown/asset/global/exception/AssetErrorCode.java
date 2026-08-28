package com.moneykk.moneytown.asset.global.exception;

import com.moneykk.moneytown.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AssetErrorCode implements ErrorCode {
    EXAMPLE_NOT_FOUND(HttpStatus.NOT_FOUND, "ASSET_404_01", "예시 코드 — 실제 사용 시 삭제하고 교체할 것");

    private final HttpStatus status;
    private final String code;
    private final String message;

    AssetErrorCode(HttpStatus status, String code, String message) {
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