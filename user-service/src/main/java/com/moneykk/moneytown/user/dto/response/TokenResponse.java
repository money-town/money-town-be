package com.moneykk.moneytown.user.dto.response;

import com.moneykk.moneytown.user.global.security.jwt.IssuedToken;

import java.time.Instant;

public record TokenResponse(String grantType,
                            String accessToken,
                            Instant accessTokenExpiresAt,
                            String refreshToken,
                            Instant refreshTokenExpiresAt) {
    public static TokenResponse from(IssuedToken accessToken, IssuedToken refreshToken) {
        return new TokenResponse(
                "Bearer",
                accessToken.value(),
                accessToken.expiresAt(),
                refreshToken.value(),
                refreshToken.expiresAt()
        );
    }
}
