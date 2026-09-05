package com.moneykk.moneytown.gateway.global.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtGrantedAuthoritiesConverterAdapter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;


/**
 * JWT role Claim을 Spring Security 권한으로 변환
 * ADMIN을 ROLE_ADMIN으로 변환하여 권한 검사에 사용
 */
@Configuration
public class JwtRoleConverterConfig {

    /**
     * JWT의 role Claim을 GrantedAuthority로 변환하는 Converter를 등록
     *
     * JWT 인증 객체를 생성하는 Reactive Converter
     */
    @Bean
    public Converter<Jwt, Mono<AbstractAuthenticationToken>>
    jwtAuthenticationConverter() {

        JwtGrantedAuthoritiesConverter authoritiesConverter =
                new JwtGrantedAuthoritiesConverter();

        // 권한으로 사용할 JWT Claim 이름
        authoritiesConverter.setAuthoritiesClaimName("role");

        // hasRole("ADMIN") 검사를 위해 ROLE_ 접두사를 추가
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
