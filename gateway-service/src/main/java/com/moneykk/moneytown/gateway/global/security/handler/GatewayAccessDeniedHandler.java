package com.moneykk.moneytown.gateway.global.security.handler;


import com.moneykk.moneytown.gateway.global.exception.GatewayErrorCode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;



@Component
public class GatewayAccessDeniedHandler implements ServerAccessDeniedHandler{

    private final SecurityErrorResponseWriter responseWriter;

    public GatewayAccessDeniedHandler(
            SecurityErrorResponseWriter responseWriter
    ) {
        this.responseWriter = responseWriter;
    }

    @Override
    public Mono<Void> handle(
            ServerWebExchange exchange,
            AccessDeniedException exception
    ) {
        return responseWriter.write(
                exchange,
                GatewayErrorCode.ACCESS_DENIED
        );
    }
}
