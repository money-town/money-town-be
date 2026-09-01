package com.moneykk.moneytown.common.security;

// 상수 관리
public final class AuthHeaderConstants {
    public static final String AUTHORIZATION = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLE = "X-User-Role";
    public static final String CORRELATION_ID = "X-Correlation-Id";

    private AuthHeaderConstants() {
    }

}
