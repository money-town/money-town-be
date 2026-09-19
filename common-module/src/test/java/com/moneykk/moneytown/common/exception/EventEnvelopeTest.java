package com.moneykk.moneytown.common.exception;
import com.moneykk.moneytown.common.event.EventEnvelope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventEnvelopeTest {
    @Test
    @DisplayName("이벤트 공통 정보를 포함한 EventEnvelope을 생성한다")
    void createEventEnvelope() {
        // given
        UUID userId = UUID.randomUUID();
        String aggregateId = userId.toString();
        String correlationId = UUID.randomUUID().toString();
        Instant before = Instant.now();

        // when
        EventEnvelope<UUID> event = EventEnvelope.of(
                "UserRegistered",
                aggregateId,
                userId,
                correlationId,
                userId
        );

        Instant after = Instant.now();

        // then
        assertThat(event.eventId()).isNotNull();
        assertThat(event.eventType()).isEqualTo("UserRegistered");
        assertThat(event.aggregateId()).isEqualTo(aggregateId);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.occurredAt()).isBetween(before, after);
        assertThat(event.correlationId()).isEqualTo(correlationId);
        assertThat(event.payload()).isEqualTo(userId);
    }
}
