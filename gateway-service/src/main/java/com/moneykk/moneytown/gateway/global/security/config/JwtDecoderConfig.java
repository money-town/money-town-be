package com.moneykk.moneytown.gateway.global.security.config;

import ch.qos.logback.core.pattern.Converter;
import com.moneykk.moneytown.gateway.global.security.jwt.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtGrantedAuthoritiesConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.util.StringUtils;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import reactor.core.publisher.Mono;


import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Set;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtDecoderConfig {

    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String ROLE_CLAIM = "role";

    private static final Set<String> ALLOWED_ROLES =
            Set.of("INVESTOR", "ISSUER", "ADMIN");


    private static final int HS256_MIN_KEY_SIZE = 32;

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(
            JwtProperties properties
    ) {
        byte[] secretBytes = decodeSecret(properties.secret());

        SecretKey secretKey = new SecretKeySpec(
                secretBytes,
                "HmacSHA256"
        );

        NimbusReactiveJwtDecoder decoder =
                NimbusReactiveJwtDecoder
                        .withSecretKey(secretKey)
                        .macAlgorithm(MacAlgorithm.HS256)
                        .build();

        OAuth2TokenValidator<Jwt> defaultValidator =
                JwtValidators.createDefaultWithIssuer(
                        requireIssuer(properties.issuer())
                );

        OAuth2TokenValidator<Jwt> tokenTypeValidator =
                new JwtClaimValidator<String>(
                        TOKEN_TYPE_CLAIM,
                        ACCESS_TOKEN_TYPE::equals
                );

        OAuth2TokenValidator<Jwt> roleValidator =
                new JwtClaimValidator<String>(
                        ROLE_CLAIM,
                        ALLOWED_ROLES::contains
                );

        OAuth2TokenValidator<Jwt> validator =
                new DelegatingOAuth2TokenValidator<>(
                        defaultValidator,
                        tokenTypeValidator,
                        roleValidator
                );

        decoder.setJwtValidator(validator);

        return decoder;
    }

    private byte[] decodeSecret(String secret) {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException(
                    "JWT_SECRET 환경변수가 설정되지 않았습니다."
            );
        }

        try {
            byte[] decodedSecret = Base64.getDecoder().decode(secret);

            if (decodedSecret.length < HS256_MIN_KEY_SIZE) {
                throw new IllegalStateException(
                        "JWT_SECRET은 Base64 디코딩 후 32바이트 이상이어야 합니다."
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

    private String requireIssuer(String issuer) {
        if (!StringUtils.hasText(issuer)) {
            throw new IllegalStateException(
                    "JWT_ISSUER 환경변수가 설정되지 않았습니다."
            );
        }

        return issuer;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            Converter<Jwt, Mono<AbstractAuthenticationToken>>
                    jwtAuthenticationConverter
    ) {

        @Bean
        public Converter<Jwt, Mono<AbstractAuthenticationToken>>
        jwtAuthenticationConverter() {

            JwtGrantedAuthoritiesConverter authoritiesConverter =
                    new JwtGrantedAuthoritiesConverter();

            authoritiesConverter.setAuthoritiesClaimName("role");
            authoritiesConverter.setAuthorityPrefix("ROLE_");

            ReactiveJwtAuthenticationConverter authenticationConverter =
                    new ReactiveJwtAuthenticationConverter();

            authenticationConverter.setJwtGrantedAuthoritiesConverter(
                    new ReactiveJwtGrantedAuthoritiesConverterAdapter(
                            authoritiesConverter
                    )
            );

            return authenticationConverter;
        }

    }
}
