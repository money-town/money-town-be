package com.moneykk.moneytown.user.global.exception;

import com.moneykk.moneytown.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum UserErrorCode implements ErrorCode {
    EMAIL_ALREADY_EXISTS(HttpStatus.NOT_FOUND, "USER_404_01", "이미 가입된 이메일 입니다."),
    DUPLICATE_PHONE(HttpStatus.NOT_FOUND ,"USER_404_02" , "없는 폰 번호 입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_404_03", "없는 회원 입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    UserErrorCode(HttpStatus status, String code, String message) {
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