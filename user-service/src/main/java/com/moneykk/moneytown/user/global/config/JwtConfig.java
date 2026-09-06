package com.moneykk.moneytown.user.global.config;

import com.moneykk.moneytown.user.global.security.jwt.JwtProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    private static final int MINIMUM_KEY_LENGTH = 32;

    // JWT 생성과 검증에 공통으로 사용하는 비밀키
    @Bean
    public SecretKey jwtSecretKey(JwtProperties properties) {
        byte[] keyBytes = decodeSecret(properties.secret());

        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    // JWT 생성
    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        ImmutableSecret<SecurityContext> secret =
                new ImmutableSecret<>(jwtSecretKey);

        return new NimbusJwtEncoder(secret);
    }

    // JWT 검증 및 해석
    @Bean
    public JwtDecoder jwtDecoder(
            SecretKey jwtSecretKey,
            JwtProperties properties
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        decoder.setJwtValidator(
                JwtValidators.createDefaultWithIssuer(properties.issuer())
        );

        return decoder;
    }

    private byte[] decodeSecret(String encodedSecret) {
        try {
            byte[] decodedSecret =
                    Base64.getDecoder().decode(encodedSecret);

            if (decodedSecret.length < MINIMUM_KEY_LENGTH) {
                throw new IllegalStateException(
                        "JWT_SECRET은 32바이트 이상이어야 합니다."
                );
            }

            return decodedSecret;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "JWT_SECRET은 올바른 Base64 문자열이어야 합니다.",
                    exception
            );
        }
    }
}