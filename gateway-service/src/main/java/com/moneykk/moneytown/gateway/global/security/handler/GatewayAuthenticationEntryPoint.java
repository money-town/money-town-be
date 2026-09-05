package com.moneykk.moneytown.gateway.global.security.handler;

import com.moneykk.moneytown.gateway.global.exception.GatewayErrorCode;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final SecurityErrorResponseWriter responseWriter;

    public GatewayAuthenticationEntryPoint(
            SecurityErrorResponseWriter responseWriter
    ) {
        this.responseWriter = responseWriter;
    }

    @Override
    public Mono<Void> commence(
            ServerWebExchange exchange,
            AuthenticationException exception
    ) {
        return responseWriter.write(
                exchange,
                GatewayErrorCode.UNAUTHORIZED
        );

    }

}
