package com.moneykk.moneytown.offering.subscription.infrastructure.event;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.offering.global.outbox.OutboxEventStore;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SubscriptionEventPublisherTest {

    private static final String POST_FDS_TOPIC =
            "subscription-events";

    @Mock
    private OutboxEventStore outboxEventStore;

    @InjectMocks
    private SubscriptionEventPublisher subscriptionEventPublisher;

    @Test
    @DisplayName("청약 한도 초과 이벤트를 청약 요청 ID와 함께 Outbox에 저장한다")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void storesSubscriptionLimitExceededEvent() {
        UUID idempotencyRequestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        String correlationId = UUID.randomUUID().toString();

        long requestedQuantity = 101L;
        long maxSubscriptionQuantity = 100L;

        subscriptionEventPublisher.publishLimitExceeded(
                idempotencyRequestId,
                userId,
                assetId,
                requestedQuantity,
                maxSubscriptionQuantity,
                correlationId
        );

        ArgumentCaptor<EventEnvelope<?>> envelopeCaptor =
                ArgumentCaptor.forClass(
                        (Class) EventEnvelope.class
                );

        verify(outboxEventStore).save(
                eq("SUBSCRIPTION_REQUEST"),
                eq(POST_FDS_TOPIC),
                envelopeCaptor.capture()
        );

        EventEnvelope<?> envelope = envelopeCaptor.getValue();

        assertThat(envelope.eventId()).isNotNull();
        assertThat(envelope.eventType())
                .isEqualTo("SubscriptionLimitExceeded");
        assertThat(envelope.aggregateId())
                .isEqualTo(idempotencyRequestId.toString());
        assertThat(envelope.userId()).isEqualTo(userId);
        assertThat(envelope.occurredAt()).isNotNull();
        assertThat(envelope.correlationId())
                .isEqualTo(correlationId);

        assertThat(envelope.payload())
                .isInstanceOf(
                        SubscriptionLimitExceededPayload.class
                );

        SubscriptionLimitExceededPayload payload =
                (SubscriptionLimitExceededPayload) envelope.payload();

        assertThat(payload.userId()).isEqualTo(userId);
        assertThat(payload.assetId()).isEqualTo(assetId);
        assertThat(payload.subscriptionId()).isNull();
        assertThat(payload.requestedQuantity())
                .isEqualTo(requestedQuantity);
        assertThat(payload.maxSubscriptionQuantity())
                .isEqualTo(maxSubscriptionQuantity);
    }

    @Test
    @DisplayName("최종 거절된 청약 실패 이벤트를 subscriptionId와 함께 Outbox에 저장한다")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void storesSubscriptionFailedEvent() {
        UUID offeringId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String correlationId = UUID.randomUUID().toString();
        String failureCode = "INSUFFICIENT_BALANCE";

        Subscription subscription = Subscription.create(
                offeringId,
                userId,
                10L,
                1_000L,
                Instant.now().plusSeconds(600)
        );

        subscription.startHoldFailureCompensation(
                failureCode
        );
        subscription.completeHoldFailureRejection();

        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.REJECTED);

        subscriptionEventPublisher.publishFailed(
                subscription,
                assetId,
                correlationId
        );

        ArgumentCaptor<EventEnvelope<?>> envelopeCaptor =
                ArgumentCaptor.forClass(
                        (Class) EventEnvelope.class
                );

        verify(outboxEventStore).save(
                eq("SUBSCRIPTION"),
                eq(POST_FDS_TOPIC),
                envelopeCaptor.capture()
        );

        EventEnvelope<?> envelope = envelopeCaptor.getValue();

        assertThat(envelope.eventId()).isNotNull();
        assertThat(envelope.eventType())
                .isEqualTo("SubscriptionFailed");
        assertThat(envelope.aggregateId())
                .isEqualTo(
                        subscription.getSubscriptionId().toString()
                );
        assertThat(envelope.userId()).isEqualTo(userId);
        assertThat(envelope.occurredAt()).isNotNull();
        assertThat(envelope.correlationId())
                .isEqualTo(correlationId);

        assertThat(envelope.payload())
                .isInstanceOf(SubscriptionFailedPayload.class);

        SubscriptionFailedPayload payload =
                (SubscriptionFailedPayload) envelope.payload();

        assertThat(payload.userId()).isEqualTo(userId);
        assertThat(payload.assetId()).isEqualTo(assetId);
        assertThat(payload.subscriptionId())
                .isEqualTo(subscription.getSubscriptionId());
        assertThat(payload.failureCode())
                .isEqualTo(failureCode);
    }
}