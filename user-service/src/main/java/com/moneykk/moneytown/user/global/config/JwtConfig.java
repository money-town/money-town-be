package com.moneykk.moneytown.user.global.config;

import com.moneykk.moneytown.user.global.security.jwt.JwtProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    private static final int MINIMUM_KEY_LENGTH = 32;

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        byte[] keyBytes = decodeSecret(properties.secret());

        SecretKey secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");

        ImmutableSecret<SecurityContext> secret =
                new ImmutableSecret<>(secretKey);

        return new NimbusJwtEncoder(secret);
    }

    private byte[] decodeSecret(String encodedSecret) {
        try {
            byte[] decodeSecret =
                    Base64.getDecoder().decode(encodedSecret);

            if(decodeSecret.length < MINIMUM_KEY_LENGTH) {
                throw new IllegalStateException(
                        "JWT_SECRET은 32바이트 이상이어야 합니다."
                );
            }

            return decodeSecret;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "JWT_SECRET은 올바른 Base64 문자열이어야 합니다.",
                    e
            );
        }
    }




}
