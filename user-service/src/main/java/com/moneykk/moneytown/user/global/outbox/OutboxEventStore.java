package com.moneykk.moneytown.user.global.outbox;

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

        OutboxEvent event = OutboxEvent.create(
                envelope.eventId(),
                aggregateType,
                parseAggregateId(envelope.aggregateId()),
                envelope.eventType(),
                topic,
                serialize(envelope)
        );

        outboxEventRepository.save(event);
    }

    private UUID parseAggregateId(String aggregateId) {
        try {
            return UUID.fromString(aggregateId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException(
                    "User Outbox aggregateId는 UUID여야 합니다.",
                    exception
            );
        }
    }

    private String serialize(EventEnvelope<?> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Outbox 이벤트 직렬화 실패. eventId=" + envelope.eventId(),
                    exception
            );
        }
    }
}
