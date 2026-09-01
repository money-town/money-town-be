package com.moneykk.moneytown.gateway.global.security.exception;

/**
 * JWT 환경설정 오류 표현
 * 잘못된 Secret 또는 Issuer로 인한 서버 시작 실패에 사용
 */

public class JwtConfigurationException extends IllegalStateException{
    public JwtConfigurationException(String message) {
        super(message);
    }

    public JwtConfigurationException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }

}
