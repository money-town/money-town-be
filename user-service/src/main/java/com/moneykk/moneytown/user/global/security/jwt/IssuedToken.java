package com.moneykk.moneytown.user.global.security.jwt;

import java.time.Instant;

public record IssuedToken(
        String value,
        Instant expiresAt,
        String tokenId
) {
}
