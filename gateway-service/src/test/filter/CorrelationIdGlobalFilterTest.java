package filter;

import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.gateway.global.filter.CorrelationIdGlobalFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdGlobalFilterTest {

    private final CorrelationIdGlobalFilter filter =
            new CorrelationIdGlobalFilter();

    @Test
    @DisplayName("Correlation ID가 없으면 새로운 UUID를 생성한다")
    void createCorrelationIdWhenHeaderIsMissing() {
        // given
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/users/me")
                .build();

        MockServerWebExchange exchange =
                MockServerWebExchange.from(request);

        AtomicReference<ServerWebExchange> capturedExchange =
                new AtomicReference<>();

        GatewayFilterChain chain = filteredExchange -> {
            capturedExchange.set(filteredExchange);
            return Mono.empty();
        };

        // when
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // then
        String correlationId = capturedExchange.get()
                .getRequest()
                .getHeaders()
                .getFirst(AuthHeaderConstants.CORRELATION_ID);

        assertThat(correlationId).isNotBlank();
        assertThatCodeIsUuid(correlationId);

        assertThat(exchange.getResponse()
                .getHeaders()
                .getFirst(AuthHeaderConstants.CORRELATION_ID))
                .isEqualTo(correlationId);
    }

    @Test
    @DisplayName("Correlation ID가 있으면 기존 값을 유지한다")
    void preserveExistingCorrelationId() {
        // given
        String existingCorrelationId = UUID.randomUUID().toString();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/users/me")
                .header(
                        AuthHeaderConstants.CORRELATION_ID,
                        existingCorrelationId
                )
                .build();

        MockServerWebExchange exchange =
                MockServerWebExchange.from(request);

        AtomicReference<ServerWebExchange> capturedExchange =
                new AtomicReference<>();

        GatewayFilterChain chain = filteredExchange -> {
            capturedExchange.set(filteredExchange);
            return Mono.empty();
        };

        // when
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // then
        String correlationId = capturedExchange.get()
                .getRequest()
                .getHeaders()
                .getFirst(AuthHeaderConstants.CORRELATION_ID);

        assertThat(correlationId).isEqualTo(existingCorrelationId);

        assertThat(exchange.getResponse()
                .getHeaders()
                .getFirst(AuthHeaderConstants.CORRELATION_ID))
                .isEqualTo(existingCorrelationId);
    }

    private void assertThatCodeIsUuid(String value) {
        assertThat(UUID.fromString(value)).isNotNull();
    }
}
