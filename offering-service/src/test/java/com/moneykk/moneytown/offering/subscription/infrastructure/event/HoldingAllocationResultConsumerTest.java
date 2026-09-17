package com.moneykk.moneytown.offering.subscription.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.offering.subscription.command.application.HoldingAllocationResultService;
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
class HoldingAllocationResultConsumerTest {

    private static final String TOPIC = "holding-allocation-result";
    private static final String CONSUMER_GROUP = "offering-service";
    private static final String CORRELATION_ID = "correlation-id";

    @Mock
    private HoldingAllocationResultService resultService;

    private ObjectMapper objectMapper;
    private HoldingAllocationResultConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();

        consumer = new HoldingAllocationResultConsumer(
                objectMapper,
                resultService
        );

        MDC.clear();
    }

    @Test
    @DisplayName("Holding 배정 성공 이벤트는 성공 처리 서비스로 전달한다")
    void routesSucceededEvent() throws Exception {
        UUID subscriptionId = UUID.randomUUID();

        EventEnvelope<HoldingAllocationSucceededPayload> envelope =
                new EventEnvelope<>(
                        UUID.randomUUID(),
                        "HoldingAllocationSucceeded",
                        subscriptionId.toString(),
                        UUID.randomUUID(),
                        Instant.now(),
                        CORRELATION_ID,
                        new HoldingAllocationSucceededPayload(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                10L,
                                "ALLOCATED"
                        )
                );

        consumer.consume(
                record(subscriptionId.toString(), envelope),
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
    @DisplayName("Holding 배정 실패 이벤트는 실패 처리 서비스로 전달한다")
    void routesFailedEvent() throws Exception {
        UUID subscriptionId = UUID.randomUUID();

        EventEnvelope<HoldingAllocationFailedPayload> envelope =
                new EventEnvelope<>(
                        UUID.randomUUID(),
                        "HoldingAllocationFailed",
                        subscriptionId.toString(),
                        UUID.randomUUID(),
                        Instant.now(),
                        CORRELATION_ID,
                        new HoldingAllocationFailedPayload(
                                UUID.randomUUID(),
                                "ALLOCATION_FAILED",
                                "배정 실패",
                                true
                        )
                );

        consumer.consume(
                record(subscriptionId.toString(), envelope),
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
    @DisplayName("Kafka key와 subscriptionId가 다르면 처리하지 않는다")
    void rejectsMismatchedPartitionKey() throws Exception {
        UUID subscriptionId = UUID.randomUUID();

        EventEnvelope<HoldingAllocationSucceededPayload> envelope =
                succeededEnvelope(subscriptionId);

        ConsumerRecord<String, String> record =
                record(UUID.randomUUID().toString(), envelope);

        assertThatThrownBy(() ->
                consumer.consume(record, CONSUMER_GROUP)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subscriptionId와 일치");

        verifyNoInteractions(resultService);
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    @DisplayName("지원하지 않는 eventType은 처리하지 않는다")
    void rejectsUnsupportedEventType() throws Exception {
        UUID subscriptionId = UUID.randomUUID();

        EventEnvelope<HoldingAllocationSucceededPayload> envelope =
                new EventEnvelope<>(
                        UUID.randomUUID(),
                        "UnsupportedEvent",
                        subscriptionId.toString(),
                        UUID.randomUUID(),
                        Instant.now(),
                        CORRELATION_ID,
                        new HoldingAllocationSucceededPayload(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                10L,
                                "ALLOCATED"
                        )
                );

        assertThatThrownBy(() ->
                consumer.consume(
                        record(subscriptionId.toString(), envelope),
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
        UUID subscriptionId = UUID.randomUUID();
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
                                subscriptionId.toString(),
                                succeededEnvelope(subscriptionId)
                        ),
                        CONSUMER_GROUP
                )
        ).isSameAs(failure);

        assertThat(MDC.get("requestId")).isNull();
    }

    private EventEnvelope<HoldingAllocationSucceededPayload>
    succeededEnvelope(UUID subscriptionId) {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                "HoldingAllocationSucceeded",
                subscriptionId.toString(),
                UUID.randomUUID(),
                Instant.now(),
                CORRELATION_ID,
                new HoldingAllocationSucceededPayload(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        10L,
                        "ALLOCATED"
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