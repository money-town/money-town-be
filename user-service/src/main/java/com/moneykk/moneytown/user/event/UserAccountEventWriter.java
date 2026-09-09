package com.moneykk.moneytown.user.event;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.user.event.dto.UserRegisteredPayload;
import com.moneykk.moneytown.user.event.dto.UserWithdrawnPayload;
import com.moneykk.moneytown.user.global.outbox.OutboxEventStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserAccountEventWriter {

    private final OutboxEventStore outboxEventStore;

    public void recordRegistered(
            UUID userId,
            String correlationId
    ) {
        EventEnvelope<UserRegisteredPayload> event = EventEnvelope.of(
                UserAccountEventConstants.USER_REGISTERED,
                userId.toString(),
                userId,
                resolveCorrelationId(correlationId),
                new UserRegisteredPayload()
        );

        outboxEventStore.save(
                UserAccountEventConstants.AGGREGATE_TYPE,
                UserAccountEventConstants.TOPIC,
                event
        );
    }

    public void recordWithdrawn(
            UUID userId,
            UUID withdrawnBy,
            String correlationId
    ) {
        EventEnvelope<UserWithdrawnPayload> event = EventEnvelope.of(
                UserAccountEventConstants.USER_WITHDRAWN,
                userId.toString(),
                userId,
                resolveCorrelationId(correlationId),
                new UserWithdrawnPayload(withdrawnBy)
        );

        outboxEventStore.save(
                UserAccountEventConstants.AGGREGATE_TYPE,
                UserAccountEventConstants.TOPIC,
                event
        );
    }

    private String resolveCorrelationId(String correlationId) {
        return StringUtils.hasText(correlationId)
                ? correlationId
                : UUID.randomUUID().toString();
    }
}
