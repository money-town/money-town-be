package com.moneykk.moneytown.gateway.global.security.config;

import com.moneykk.moneytown.gateway.global.security.handler.GatewayAccessDeniedHandler;
import com.moneykk.moneytown.gateway.global.security.handler.GatewayAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final GatewayAuthenticationEntryPoint authenticationEntryPoint;
    private final GatewayAccessDeniedHandler accessDeniedHandler;



    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            Converter<Jwt, Mono<AbstractAuthenticationToken>>
                    jwtAuthenticationConverter
    ) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .cors(Customizer.withDefaults())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))

                .securityContextRepository(
                        NoOpServerSecurityContextRepository.getInstance()
                )

                .authorizeExchange(exchange -> exchange
                        // CORS 사전 요청
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 공개 Auth API
                        .pathMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/signup",
                                "/api/v1/auth/login",
                                "/api/v1/auth/reissue"
                        ).permitAll()

                        // Swagger 및 상태 확인
                        .pathMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/health"
                        ).permitAll()

                        // 내 정보 조회·수정·탈퇴
                        .pathMatchers(HttpMethod.GET, "/api/v1/users/me").authenticated()
                        .pathMatchers(HttpMethod.PATCH, "/api/v1/users/me").authenticated()
                        .pathMatchers(HttpMethod.DELETE, "/api/v1/users/me").authenticated()

                        // 관리자 사용자 목록·단건 조회
                        .pathMatchers(
                                HttpMethod.GET,
                                "/api/v1/users",
                                "/api/v1/users/{userId}"
                        ).hasRole("ADMIN")

                        // 관리자 사용자 수정
                        .pathMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/users/{userId}"
                        ).hasRole("ADMIN")

                        // 관리자 사용자 탈퇴
                        .pathMatchers(
                                HttpMethod.DELETE,
                                "/api/v1/users/{userId}"
                        ).hasRole("ADMIN")

                        // KYC 신청
                        .pathMatchers(
                                HttpMethod.POST,
                                "/api/v1/kyc-verifications"
                        ).authenticated()

                        // 내 KYC 현재 상태 조회
                        .pathMatchers(
                                HttpMethod.GET,
                                "/api/v1/kyc-verifications/me/current"
                        ).authenticated()

                        // 내 KYC 이력 조회 — 명세 기준 ADMIN
                        .pathMatchers(
                                HttpMethod.GET,
                                "/api/v1/kyc-verifications/me"
                        ).authenticated()

                        // 관리자 KYC 심사 목록·단건 조회
                        .pathMatchers(
                                HttpMethod.GET,
                                "/api/v1/kyc-verifications",
                                "/api/v1/kyc-verifications/{kycId}"
                        ).hasRole("ADMIN")

                        // 관리자 KYC 승인·거절
                        .pathMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/kyc-verifications/{kycId}/approve",
                                "/api/v1/kyc-verifications/{kycId}/reject"
                        ).hasRole("ADMIN")

                        // 나머지 API는 JWT 인증 필요
                        .anyExchange().authenticated()
                )

                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationConverter))
                )

                .build();
    }
}
