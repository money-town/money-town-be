package com.moneykk.moneytown.gateway.global.security.jwt;

import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered{
    private static final String ROLE_CLAIM = "role";

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain
    ) {
        return exchange.getPrincipal()
                .ofType(JwtAuthenticationToken.class)
                .map(authentication ->
                        addAuthenticationHeaders(
                                exchange,
                                authentication.getToken()
                        )
                )
                .defaultIfEmpty(removeAuthenticationHeaders(exchange))
                .flatMap(chain::filter);
    }

    private ServerWebExchange addAuthenticationHeaders(
            ServerWebExchange exchange,
            Jwt jwt
    ) {
        String userId = jwt.getSubject();
        String role = jwt.getClaimAsString(ROLE_CLAIM);

        validateClaims(userId, role);

        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers(headers -> {
                    // 외부에서 임의로 전달한 헤더 제거
                    headers.remove(AuthHeaderConstants.USER_ID);
                    headers.remove(AuthHeaderConstants.USER_ROLE);

                    // 검증된 JWT 정보로 다시 설정
                    headers.set(AuthHeaderConstants.USER_ID, userId);
                    headers.set(AuthHeaderConstants.USER_ROLE, role);
                })
                .build();

        return exchange.mutate()
                .request(request)
                .build();
    }

    private ServerWebExchange removeAuthenticationHeaders(
            ServerWebExchange exchange
    ) {
        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers(headers -> {
                    headers.remove(AuthHeaderConstants.USER_ID);
                    headers.remove(AuthHeaderConstants.USER_ROLE);
                })
                .build();

        return exchange.mutate()
                .request(request)
                .build();
    }

    private void validateClaims(String userId, String role) {
        if (!StringUtils.hasText(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "JWT에 사용자 ID가 없습니다."
            );
        }

        try {
            UUID.fromString(userId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "JWT 사용자 ID 형식이 올바르지 않습니다."
            );
        }

        if (!StringUtils.hasText(role)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "JWT에 사용자 권한이 없습니다."
            );
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

}
