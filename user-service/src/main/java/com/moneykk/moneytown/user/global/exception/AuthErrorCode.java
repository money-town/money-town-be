package com.moneykk.moneytown.user.global.exception;

import com.moneykk.moneytown.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements ErrorCode {
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_401_01", "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED,"AUTH_401_02","유효하지 않은 Refresh Token입니다.");





    private final HttpStatus status;
    private final String code;
    private final String message;

    AuthErrorCode(HttpStatus status, String code, String message) {
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
