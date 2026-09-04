package com.moneykk.moneytown.user.global.security.jwt;

import com.moneykk.moneytown.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String ROLE_CLAIM = "role";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";

    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String REFRESH_TOKEN_TYPE = "REFRESH";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public IssuedToken issueAccessToken(User user) {
        return issueToken(
                user,
                ACCESS_TOKEN_TYPE,
                jwtProperties.accessTokenExpiration(),
                true
        );
    }

    public IssuedToken issueRefreshToken(User user) {
        return issueToken(
                user,
                REFRESH_TOKEN_TYPE,
                jwtProperties.refreshTokenExpiration(),
                false
        );
    }

    private IssuedToken issueToken(
            User user,
            String tokenType,
            Duration expiration,
            boolean includeRole
    ) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(expiration);
        String tokenId = UUID.randomUUID().toString();

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(user.getUserId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(tokenId)
                .claim(TOKEN_TYPE_CLAIM, tokenType);

        if (includeRole) {
            claims.claim(ROLE_CLAIM, user.getRole().name());
        }

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .type("JWT")
                .build();

        String token = jwtEncoder.encode(
                JwtEncoderParameters.from(
                        header,
                        claims.build()
                )
        ).getTokenValue();

        return new IssuedToken(
                token,
                expiresAt,
                tokenId
        );
    }






}
