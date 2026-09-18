package com.moneykk.moneytown.offering.global.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.moneykk.moneytown.common.event.EventEnvelope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventStoreTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private OutboxEventStore outboxEventStore;

    private ObjectMapper newObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private EventEnvelope<String> envelope(String aggregateId) {
        return EventEnvelope.of(
                "SubscriptionReserved",
                aggregateId,
                UUID.randomUUID(),
                "correlation-id",
                "payload"
        );
    }

    @Test
    @DisplayName("envelope을 직렬화하여 Outbox 이벤트로 저장한다")
    void savesSerializedOutboxEvent() {
        // given
        outboxEventStore = new OutboxEventStore(
                outboxEventRepository, newObjectMapper()
        );

        UUID aggregateId = UUID.randomUUID();
        EventEnvelope<String> envelope = envelope(aggregateId.toString());

        // when
        outboxEventStore.save("SUBSCRIPTION", "subscription-reserved", envelope);

        // then
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("envelope이 없으면 저장을 거부한다")
    void rejectsNullEnvelope() {
        // given
        outboxEventStore = new OutboxEventStore(
                outboxEventRepository, newObjectMapper()
        );

        // when & then
        assertThatThrownBy(() ->
                outboxEventStore.save("SUBSCRIPTION", "topic", null)
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("aggregateId가 없으면 저장을 거부한다")
    void rejectsMissingAggregateId() {
        // given
        outboxEventStore = new OutboxEventStore(
                outboxEventRepository, newObjectMapper()
        );

        EventEnvelope<String> envelope = envelope(" ");

        // when & then
        assertThatThrownBy(() ->
                outboxEventStore.save("SUBSCRIPTION", "topic", envelope)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("aggregateId가 UUID 형식이 아니면 저장을 거부한다")
    void rejectsInvalidAggregateIdFormat() {
        // given
        outboxEventStore = new OutboxEventStore(
                outboxEventRepository, newObjectMapper()
        );

        EventEnvelope<String> envelope = envelope("not-a-uuid");

        // when & then
        assertThatThrownBy(() ->
                outboxEventStore.save("SUBSCRIPTION", "topic", envelope)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("envelope 직렬화에 실패하면 저장을 거부한다")
    void rejectsWhenSerializationFails() throws JsonProcessingException {
        // given
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(mock(JsonProcessingException.class));

        outboxEventStore = new OutboxEventStore(
                outboxEventRepository, objectMapper
        );

        EventEnvelope<String> envelope = envelope(UUID.randomUUID().toString());

        // when & then
        assertThatThrownBy(() ->
                outboxEventStore.save("SUBSCRIPTION", "topic", envelope)
        ).isInstanceOf(IllegalStateException.class);
    }
}
