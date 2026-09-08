package com.moneykk.moneytown.offering.global.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.common.event.EventEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboxEventStore {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void save(
            String aggregateType,
            String topic,
            EventEnvelope<?> envelope
    ) {
        Objects.requireNonNull(envelope, "envelope은 필수입니다.");

        UUID aggregateId = parseAggregateId(envelope.aggregateId());
        String envelopeJson = serialize(envelope);

        OutboxEvent outboxEvent = OutboxEvent.create(
                envelope.eventId(),
                aggregateType,
                aggregateId,
                envelope.eventType(),
                topic,
                envelopeJson
        );

        outboxEventRepository.save(outboxEvent);
    }

    private UUID parseAggregateId(String aggregateId) {
        if (aggregateId == null || aggregateId.isBlank()) {
            throw new IllegalArgumentException("aggregateId는 필수입니다.");
        }

        try {
            return UUID.fromString(aggregateId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Offering Outbox의 aggregateId는 UUID여야 합니다.",
                    e
            );
        }
    }

    private String serialize(EventEnvelope<?> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Outbox 이벤트 직렬화에 실패했습니다. eventId="
                            + envelope.eventId(),
                    e
            );
        }
    }
}