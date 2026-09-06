package com.moneykk.moneytown.user.entity;

import com.moneykk.moneytown.common.entity.BaseEntity;
import com.moneykk.moneytown.user.global.security.jwt.IssuedToken;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Table(name = "p_refresh_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "refresh_token_id", nullable = false, updatable = false)
    private UUID refreshTokenId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_id", nullable = false, unique = true, updatable = false, length = 36)
    private String tokenId;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public static RefreshToken create(UUID userId, IssuedToken issuedToken) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.userId = userId;
        refreshToken.tokenId = issuedToken.tokenId();
        refreshToken.expiresAt = issuedToken.expiresAt();
        return refreshToken;
    }

    public void revoke() {
        if (revokedAt == null) {
            revokedAt = Instant.now();
        }
    }
}
