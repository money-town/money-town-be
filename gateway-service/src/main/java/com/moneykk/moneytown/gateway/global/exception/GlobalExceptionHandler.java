package com.moneykk.moneytown.gateway.global.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.exception.ErrorCode;
import com.moneykk.moneytown.common.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Order(-2)
@Component
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(exception);
        }

        HttpStatus status;
        String code;
        String message;
        if (exception instanceof BusinessException businessException) {
            ErrorCode errorCode = businessException.getErrorCode();
            status = errorCode.getStatus();
            code = errorCode.getCode();
            message = errorCode.getMessage();
        } else if (exception instanceof ResponseStatusException responseStatusException) {
            status = HttpStatus.valueOf(responseStatusException.getStatusCode().value());
            code = "GATEWAY_" + status.value();
            message = responseStatusException.getReason() == null
                    ? "요청 처리 중 오류가 발생했습니다." : responseStatusException.getReason();
        } else {
            log.error("Unhandled gateway exception", exception);
            status = GatewayErrorCode.GATEWAY_ERROR.getStatus();
            code = GatewayErrorCode.GATEWAY_ERROR.getCode();
            message = GatewayErrorCode.GATEWAY_ERROR.getMessage();
        }

        ApiResponse<Void> body = ApiResponse.error(code, message);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            DataBuffer buffer = exchange.getResponse().bufferFactory()
                    .wrap(objectMapper.writeValueAsBytes(body));
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (JsonProcessingException serializationException) {
            return Mono.error(serializationException);
        }
    }
}