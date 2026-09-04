package com.moneykk.moneytown.user.dto.response;

import com.moneykk.moneytown.user.entity.User;
import com.moneykk.moneytown.user.entity.type.UserRole;
import com.moneykk.moneytown.user.global.security.jwt.IssuedToken;

import java.time.Instant;
import java.util.UUID;

public record LoginResponse(     UUID userId,
                                 String email,
                                 UserRole role,
                                 String grantType,
                                 String accessToken,
                                 Instant accessTokenExpiresAt,
                                 String refreshToken,
                                 Instant refreshTokenExpiresAt) {


    public static LoginResponse from(
            User user,
            IssuedToken accessToken,
            IssuedToken refreshToken
    ) {
        return new LoginResponse(
                user.getUserId(),
                user.getEmail(),
                user.getRole(),
                "Bearer",
                accessToken.value(),
                accessToken.expiresAt(),
                refreshToken.value(),
                refreshToken.expiresAt()
        );
    }
}
