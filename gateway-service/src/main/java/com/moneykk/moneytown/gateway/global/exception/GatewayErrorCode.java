package com.moneykk.moneytown.gateway.global.exception;

import com.moneykk.moneytown.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum GatewayErrorCode implements ErrorCode {
    GATEWAY_ERROR(HttpStatus.BAD_GATEWAY, "GATEWAY_502", "서비스 요청 처리 중 오류가 발생했습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED,"GATEWAY_401" , "인증이 필요하거나 인증 정보가 유효하지 않습니다."),

    ACCESS_DENIED(HttpStatus.FORBIDDEN,"GATEWAY_403", "해당 요청을 수행할 권한이 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    GatewayErrorCode(HttpStatus status, String code, String message) {
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