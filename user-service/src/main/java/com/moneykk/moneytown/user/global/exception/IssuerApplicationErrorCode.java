package com.moneykk.moneytown.user.global.exception;

import com.moneykk.moneytown.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum IssuerApplicationErrorCode implements ErrorCode {
    APPLICATION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "ISSUER_404_01",
            "발행자 권한 신청 정보를 찾을 수 없습니다."
    ),
    ADMIN_REQUIRED(
            HttpStatus.FORBIDDEN,
            "ISSUER_403_01",
            "관리자만 발행자 권한 신청을 심사할 수 있습니다."
    ),
    APPLICATION_ALREADY_PENDING(
            HttpStatus.CONFLICT,
            "ISSUER_409_01",
            "이미 심사 중인 발행자 권한 신청이 있습니다."
    ),
    APPLICATION_NOT_PENDING(
            HttpStatus.CONFLICT,
            "ISSUER_409_02",
            "심사 대기 상태의 발행자 권한 신청만 처리할 수 있습니다."
    ),
    ALREADY_ISSUER(
            HttpStatus.CONFLICT,
            "ISSUER_409_03",
            "이미 발행자 권한을 보유하고 있습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    IssuerApplicationErrorCode(
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
