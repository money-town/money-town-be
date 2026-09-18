package com.moneykk.moneytown.analysis.fds.infrastructure.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.analysis.fds.command.application.PostFdsService;
import com.moneykk.moneytown.analysis.fds.infrastructure.kafka.event.SubscriptionEventPayload;
import com.moneykk.moneytown.analysis.fds.infrastructure.kafka.exception.SubscriptionEventDeserializationException;
import com.moneykk.moneytown.common.event.EventEnvelope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SubscriptionEventConsumerTest {

    @Mock
    private PostFdsService postFdsService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private SubscriptionEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new SubscriptionEventConsumer(objectMapper, postFdsService);
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private EventEnvelope<SubscriptionEventPayload> sampleEnvelope() {
        UUID userId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        return new EventEnvelope<>(
                UUID.randomUUID(), "SubscriptionSuccess", subscriptionId.toString(), userId,
                Instant.now(), "corr-1",
                new SubscriptionEventPayload(userId, assetId, subscriptionId, null, null)
        );
    }

    private String toJson(EventEnvelope<SubscriptionEventPayload> envelope) throws Exception {
        return objectMapper.writeValueAsString(envelope);
    }

    @Test
    @DisplayName("정상 메시지면 파싱해서 PostFdsService에 위임하고 처리 후 MDC를 정리한다")
    void consumeSubscriptionEvent_validMessage_delegatesAndClearsMdc() throws Exception {
        EventEnvelope<SubscriptionEventPayload> envelope = sampleEnvelope();

        consumer.consumeSubscriptionEvent(toJson(envelope));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<EventEnvelope<SubscriptionEventPayload>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(postFdsService).handle(captor.capture());
        assertThat(captor.getValue().eventId()).isEqualTo(envelope.eventId());
        assertThat(captor.getValue().payload().userId()).isEqualTo(envelope.payload().userId());
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    @DisplayName("역직렬화에 실패하면 PostFdsService를 호출하지 않고 전용 예외를 던진다")
    void consumeSubscriptionEvent_invalidJson_throwsWithoutCallingPostFdsService() {
        assertThatThrownBy(() -> consumer.consumeSubscriptionEvent("{invalid-json"))
                .isInstanceOf(SubscriptionEventDeserializationException.class);

        verifyNoInteractions(postFdsService);
    }

    @Test
    @DisplayName("PostFdsService가 예외를 던지면 그대로 전파하면서도 MDC는 정리한다")
    void consumeSubscriptionEvent_postFdsServiceThrows_propagatesAndClearsMdc() throws Exception {
        EventEnvelope<SubscriptionEventPayload> envelope = sampleEnvelope();
        doThrow(new RuntimeException("redis down")).when(postFdsService).handle(any());

        assertThatThrownBy(() -> consumer.consumeSubscriptionEvent(toJson(envelope)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("redis down");

        assertThat(MDC.get("requestId")).isNull();
    }
}
