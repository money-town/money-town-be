package com.moneykk.moneytown.user.global.exception;

import com.moneykk.moneytown.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum UserErrorCode implements ErrorCode {
    ACCOUNT_UNAVAILABLE(HttpStatus.UNAUTHORIZED, "USER_401_01", "계정 정지 상태입니다." ),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_404_01", "사용자를 찾을 수 없습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER_409_01", "이미 가입된 이메일 입니다."),
    PHONE_ALREADY_EXISTS(HttpStatus.CONFLICT,"USER_409_02","이미 가입된 휴대전화 번호 입니다."),
    ADMIN_SELF_WITHDRAWAL_NOT_ALLOWED(HttpStatus.FORBIDDEN,"ADMIN_403_01","관리자는 본인 계정을 탈퇴 처리할 수 없습니다."),
    ADMIN_WITHDRAWAL_NOT_ALLOWED(HttpStatus.FORBIDDEN,"ADMIN_403_02", "관리자 계정은 탈퇴 처리할 수 없습니다.");


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