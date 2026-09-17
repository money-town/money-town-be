package com.moneykk.moneytown.offering.subscription.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.offering.subscription.command.application.WalletHoldResultService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class WalletHoldResultConsumerTest {

    private static final String TOPIC = "wallet-hold-result";
    private static final String CONSUMER_GROUP = "offering-service";
    private static final String CORRELATION_ID = "correlation-id";

    @Mock
    private WalletHoldResultService resultService;

    private ObjectMapper objectMapper;
    private WalletHoldResultConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();

        consumer = new WalletHoldResultConsumer(
                objectMapper,
                resultService
        );

        MDC.clear();
    }

    @Test
    @DisplayName("Wallet 동결 성공 이벤트는 성공 처리 서비스로 전달한다")
    void routesSucceededEvent() throws Exception {
        UUID userId = UUID.randomUUID();

        consumer.consume(
                record(
                        userId.toString(),
                        succeededEnvelope(userId)
                ),
                CONSUMER_GROUP
        );

        verify(resultService).handleSucceeded(
                any(),
                eq(CONSUMER_GROUP)
        );
        verify(resultService, never()).handleFailed(any(), any());
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    @DisplayName("Wallet 동결 실패 이벤트는 실패 처리 서비스로 전달한다")
    void routesFailedEvent() throws Exception {
        UUID userId = UUID.randomUUID();

        EventEnvelope<WalletHoldFailedPayload> envelope =
                new EventEnvelope<>(
                        UUID.randomUUID(),
                        "WalletHoldFailed",
                        UUID.randomUUID().toString(),
                        userId,
                        Instant.now(),
                        CORRELATION_ID,
                        new WalletHoldFailedPayload(
                                1L,
                                "FAILED",
                                "잔액 부족"
                        )
                );

        consumer.consume(
                record(userId.toString(), envelope),
                CONSUMER_GROUP
        );

        verify(resultService).handleFailed(
                any(),
                eq(CONSUMER_GROUP)
        );
        verify(resultService, never()).handleSucceeded(any(), any());
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    @DisplayName("Kafka key와 userId가 다르면 처리하지 않는다")
    void rejectsMismatchedPartitionKey() throws Exception {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() ->
                consumer.consume(
                        record(
                                UUID.randomUUID().toString(),
                                succeededEnvelope(userId)
                        ),
                        CONSUMER_GROUP
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId와 일치");

        verifyNoInteractions(resultService);
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    @DisplayName("지원하지 않는 eventType은 처리하지 않는다")
    void rejectsUnsupportedEventType() throws Exception {
        UUID userId = UUID.randomUUID();

        EventEnvelope<WalletHoldSucceededPayload> envelope =
                new EventEnvelope<>(
                        UUID.randomUUID(),
                        "UnsupportedEvent",
                        UUID.randomUUID().toString(),
                        userId,
                        Instant.now(),
                        CORRELATION_ID,
                        new WalletHoldSucceededPayload(
                                1L,
                                2L,
                                "HELD"
                        )
                );

        assertThatThrownBy(() ->
                consumer.consume(
                        record(userId.toString(), envelope),
                        CONSUMER_GROUP
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지원하지 않는");

        verifyNoInteractions(resultService);
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    @DisplayName("잘못된 JSON은 역직렬화 예외를 발생시킨다")
    void rejectsMalformedJson() {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>(
                        TOPIC,
                        0,
                        0L,
                        UUID.randomUUID().toString(),
                        "{invalid-json"
                );

        assertThatThrownBy(() ->
                consumer.consume(record, CONSUMER_GROUP)
        ).isInstanceOf(JsonProcessingException.class);

        verifyNoInteractions(resultService);
    }

    @Test
    @DisplayName("서비스 예외를 전파하고 MDC를 정리한다")
    void propagatesServiceExceptionAndClearsMdc() throws Exception {
        UUID userId = UUID.randomUUID();
        RuntimeException failure = new RuntimeException("처리 실패");

        doAnswer(invocation -> {
            assertThat(MDC.get("requestId"))
                    .isEqualTo(CORRELATION_ID);
            throw failure;
        }).when(resultService).handleSucceeded(
                any(),
                eq(CONSUMER_GROUP)
        );

        assertThatThrownBy(() ->
                consumer.consume(
                        record(
                                userId.toString(),
                                succeededEnvelope(userId)
                        ),
                        CONSUMER_GROUP
                )
        ).isSameAs(failure);

        assertThat(MDC.get("requestId")).isNull();
    }

    private EventEnvelope<WalletHoldSucceededPayload>
    succeededEnvelope(UUID userId) {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                "WalletHoldSucceeded",
                UUID.randomUUID().toString(),
                userId,
                Instant.now(),
                CORRELATION_ID,
                new WalletHoldSucceededPayload(
                        1L,
                        2L,
                        "HELD"
                )
        );
    }

    private ConsumerRecord<String, String> record(
            String key,
            Object envelope
    ) throws JsonProcessingException {
        return new ConsumerRecord<>(
                TOPIC,
                0,
                0L,
                key,
                objectMapper.writeValueAsString(envelope)
        );
    }
}