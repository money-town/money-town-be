package com.moneykk.moneytown.gateway.global.filter;

import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered{
    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain
    ) {
        String requestCorrelationId = exchange.getRequest()
                .getHeaders()
                .getFirst(AuthHeaderConstants.CORRELATION_ID);

        String correlationId = StringUtils.hasText(requestCorrelationId)
                ? requestCorrelationId
                : UUID.randomUUID().toString();


        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers(headers ->
                        headers.set(
                                AuthHeaderConstants.CORRELATION_ID,
                                correlationId
                        )
                )
                .build();

        exchange.getResponse()
                .getHeaders()
                .set(AuthHeaderConstants.CORRELATION_ID, correlationId);

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(request)
                .build();

        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

}
