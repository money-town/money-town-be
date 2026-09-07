package com.moneykk.moneytown.user.global.exception;

import com.moneykk.moneytown.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum KycErrorCode implements ErrorCode {
    KYC_NOT_FOUND(HttpStatus.NOT_FOUND, "KYC_404_01", "KYC 신청 정보를 찾을 수 없습니다."),

    KYC_ALREADY_PENDING(HttpStatus.CONFLICT, "KYC_409_01", "이미 심사 중인 KYC 신청이 있습니다."),

    KYC_NOT_PENDING(HttpStatus.CONFLICT, "KYC_409_02", "심사 대기 상태의 KYC만 처리할 수 있습니다.");


    private final HttpStatus status;
    private final String code;
    private final String message;

    KycErrorCode(HttpStatus status, String code, String message) {
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

