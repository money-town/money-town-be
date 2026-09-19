package com.moneykk.moneytown.offering.subscription.infrastructure.event;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.offering.global.outbox.OutboxEventStore;
import com.moneykk.moneytown.offering.subscription.domain.entity.CancellationType;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SubscriptionEventPublisherTest {

    private static final String POST_FDS_TOPIC =
            "subscription-events";

    private static final String COMPENSATION_REQUESTED_TOPIC =
            "subscription-compensation-requested";

    private static final String RESERVED_TOPIC =
            "subscription-reserved";

    private static final String CONFIRMED_TOPIC =
            "subscription-confirmed";

    String failureReasonCode = "INSUFFICIENT_AVAILABLE_BALANCE";

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
    @DisplayName("Wallet HOLD 실패 이벤트에 실패 출처와 사유 코드를 포함해 Outbox에 저장한다")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void storesSubscriptionFailedEvent() {
        UUID offeringId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String correlationId = UUID.randomUUID().toString();

        Subscription subscription = Subscription.create(
                offeringId,
                userId,
                10L,
                1_000L,
                Instant.now().plusSeconds(600)
        );

        subscription.startHoldFailureCompensation(
                failureReasonCode
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
        assertThat(payload.failureSource())
                .isEqualTo("WALLET_HOLD");
        assertThat(payload.failureReasonCode())
                .isEqualTo(failureReasonCode);
    }

    @Test
    @DisplayName("공모 취소 보상 요청은 cancellationType을 사유로 Outbox에 저장한다")
    void storesOfferingCancellationCompensationRequestedEvent() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();

        Subscription subscription = Subscription.create(
                offeringId,
                userId,
                10L,
                1_000L,
                Instant.now().plusSeconds(600)
        );

        subscription.startCompensation(
                CancellationType.OFFERING_ADMIN_CANCELLED
        );

        // when
        subscriptionEventPublisher.publishCompensationRequested(
                subscription,
                assetId,
                correlationId
        );

        // then
        EventEnvelope<?> envelope =
                captureCompensationRequestedEnvelope();

        assertThat(envelope.eventId()).isNotNull();
        assertThat(envelope.eventType())
                .isEqualTo("SubscriptionCompensationRequested");
        assertThat(envelope.aggregateId())
                .isEqualTo(subscription.getSubscriptionId().toString());
        assertThat(envelope.userId()).isEqualTo(userId);
        assertThat(envelope.occurredAt()).isNotNull();
        assertThat(envelope.correlationId())
                .isEqualTo(correlationId);

        assertThat(envelope.payload())
                .isInstanceOf(
                        SubscriptionCompensationRequestedPayload.class
                );

        SubscriptionCompensationRequestedPayload payload =
                (SubscriptionCompensationRequestedPayload)
                        envelope.payload();

        assertThat(payload.offeringId()).isEqualTo(offeringId);
        assertThat(payload.assetId()).isEqualTo(assetId);
        assertThat(payload.reason())
                .isEqualTo("OFFERING_ADMIN_CANCELLED");
    }

    @Test
    @DisplayName("예약 만료 보상 요청은 RESERVATION_EXPIRED를 사유로 Outbox에 저장한다")
    void storesReservationExpirationCompensationRequestedEvent() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();

        Instant reservationExpiresAt =
                Instant.now().plusSeconds(60);

        Subscription subscription = Subscription.create(
                offeringId,
                userId,
                10L,
                1_000L,
                reservationExpiresAt
        );

        subscription.startExpirationCompensation(
                reservationExpiresAt.plusSeconds(1)
        );

        // when
        subscriptionEventPublisher.publishCompensationRequested(
                subscription,
                assetId,
                correlationId
        );

        // then
        EventEnvelope<?> envelope =
                captureCompensationRequestedEnvelope();

        assertThat(envelope.eventType())
                .isEqualTo("SubscriptionCompensationRequested");
        assertThat(envelope.aggregateId())
                .isEqualTo(subscription.getSubscriptionId().toString());
        assertThat(envelope.userId()).isEqualTo(userId);
        assertThat(envelope.correlationId())
                .isEqualTo(correlationId);

        assertThat(envelope.payload())
                .isInstanceOf(
                        SubscriptionCompensationRequestedPayload.class
                );

        SubscriptionCompensationRequestedPayload payload =
                (SubscriptionCompensationRequestedPayload)
                        envelope.payload();

        assertThat(payload.offeringId()).isEqualTo(offeringId);
        assertThat(payload.assetId()).isEqualTo(assetId);
        assertThat(payload.reason())
                .isEqualTo("RESERVATION_EXPIRED");
    }

    @Test
    @DisplayName("허용된 보상 사유가 없는 청약은 보상 요청을 저장하지 않는다")
    void rejectsCompensationRequestedEventWithoutSupportedReason() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();

        Subscription subscription = Subscription.create(
                offeringId,
                userId,
                10L,
                1_000L,
                Instant.now().plusSeconds(600)
        );

        subscription.startHoldFailureCompensation(
                "INSUFFICIENT_AVAILABLE_BALANCE"
        );

        // when & then
        assertThatThrownBy(
                () -> subscriptionEventPublisher
                        .publishCompensationRequested(
                                subscription,
                                assetId,
                                correlationId
                        )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "보상 요청을 발행할 수 있는 사유가 없습니다."
                );

        verifyNoInteractions(outboxEventStore);
    }

    @Test
    @DisplayName("PROCESSING 청약의 동결 요청 이벤트를 Outbox에 저장한다")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void storesSubscriptionReservedEvent() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();

        Subscription subscription = Subscription.create(
                offeringId, userId, 10L, 1_000L,
                Instant.now().plusSeconds(600)
        );

        // when
        subscriptionEventPublisher.publishReserved(
                subscription, correlationId
        );

        // then
        ArgumentCaptor<EventEnvelope<?>> envelopeCaptor =
                ArgumentCaptor.forClass((Class) EventEnvelope.class);

        verify(outboxEventStore).save(
                eq("SUBSCRIPTION"),
                eq(RESERVED_TOPIC),
                envelopeCaptor.capture()
        );

        EventEnvelope<?> envelope = envelopeCaptor.getValue();
        assertThat(envelope.eventType())
                .isEqualTo("SubscriptionReserved");
        assertThat(envelope.aggregateId())
                .isEqualTo(subscription.getSubscriptionId().toString());
        assertThat(envelope.payload())
                .isInstanceOf(SubscriptionReservedPayload.class);
    }

    @Test
    @DisplayName("PROCESSING 상태가 아닌 청약은 동결 요청 이벤트를 저장하지 않는다")
    void rejectsReservedEventForNonProcessingSubscription() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Subscription subscription = Subscription.create(
                offeringId, userId, 10L, 1_000L,
                Instant.now().plusSeconds(600)
        );
        subscription.markHoldSucceeded();

        // when & then
        assertThatThrownBy(() ->
                subscriptionEventPublisher.publishReserved(
                        subscription, UUID.randomUUID().toString()
                )
        ).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(outboxEventStore);
    }

    @Test
    @DisplayName("correlationId가 비어 있으면 이벤트를 저장하지 않는다")
    void rejectsBlankCorrelationId() {
        // given
        Subscription subscription = Subscription.create(
                UUID.randomUUID(), UUID.randomUUID(), 10L, 1_000L,
                Instant.now().plusSeconds(600)
        );

        // when & then
        assertThatThrownBy(() ->
                subscriptionEventPublisher.publishReserved(
                        subscription, " "
                )
        ).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(outboxEventStore);
    }

    @Test
    @DisplayName("CONFIRMED 청약의 확정 이벤트를 Outbox에 저장한다")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void storesSubscriptionConfirmedEvent() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();

        Subscription subscription = Subscription.create(
                offeringId, userId, 10L, 1_000L,
                Instant.now().plusSeconds(600)
        );
        subscription.markHoldSucceeded();
        subscription.confirm(Instant.now());

        // when
        subscriptionEventPublisher.publishConfirmed(
                subscription, assetId, correlationId
        );

        // then
        ArgumentCaptor<EventEnvelope<?>> envelopeCaptor =
                ArgumentCaptor.forClass((Class) EventEnvelope.class);

        verify(outboxEventStore).save(
                eq("SUBSCRIPTION"),
                eq(CONFIRMED_TOPIC),
                envelopeCaptor.capture()
        );

        EventEnvelope<?> envelope = envelopeCaptor.getValue();
        assertThat(envelope.eventType())
                .isEqualTo("SubscriptionConfirmed");

        SubscriptionConfirmedPayload payload =
                (SubscriptionConfirmedPayload) envelope.payload();
        assertThat(payload.offeringId()).isEqualTo(offeringId);
        assertThat(payload.assetId()).isEqualTo(assetId);
        assertThat(payload.quantity()).isEqualTo(10L);
    }

    @Test
    @DisplayName("CONFIRMED 상태가 아닌 청약은 확정 이벤트를 저장하지 않는다")
    void rejectsConfirmedEventForNonConfirmedSubscription() {
        // given
        Subscription subscription = Subscription.create(
                UUID.randomUUID(), UUID.randomUUID(), 10L, 1_000L,
                Instant.now().plusSeconds(600)
        );

        // when & then
        assertThatThrownBy(() ->
                subscriptionEventPublisher.publishConfirmed(
                        subscription,
                        UUID.randomUUID(),
                        UUID.randomUUID().toString()
                )
        ).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(outboxEventStore);
    }

    @Test
    @DisplayName("assetId가 없으면 확정 이벤트를 저장하지 않는다")
    void rejectsConfirmedEventWithoutAssetId() {
        // given
        Subscription subscription = Subscription.create(
                UUID.randomUUID(), UUID.randomUUID(), 10L, 1_000L,
                Instant.now().plusSeconds(600)
        );
        subscription.markHoldSucceeded();
        subscription.confirm(Instant.now());

        // when & then
        assertThatThrownBy(() ->
                subscriptionEventPublisher.publishConfirmed(
                        subscription, null,
                        UUID.randomUUID().toString()
                )
        ).isInstanceOf(NullPointerException.class);

        verifyNoInteractions(outboxEventStore);
    }

    @Test
    @DisplayName("COMPENSATING 상태가 아닌 청약은 보상 요청 이벤트를 저장하지 않는다")
    void rejectsCompensationRequestedForNonCompensatingSubscription() {
        // given
        Subscription subscription = Subscription.create(
                UUID.randomUUID(), UUID.randomUUID(), 10L, 1_000L,
                Instant.now().plusSeconds(600)
        );

        // when & then
        assertThatThrownBy(() ->
                subscriptionEventPublisher.publishCompensationRequested(
                        subscription,
                        UUID.randomUUID(),
                        UUID.randomUUID().toString()
                )
        ).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(outboxEventStore);
    }

    @Test
    @DisplayName("REJECTED 상태가 아닌 청약은 실패 이벤트를 저장하지 않는다")
    void rejectsFailedEventForNonRejectedSubscription() {
        // given
        Subscription subscription = Subscription.create(
                UUID.randomUUID(), UUID.randomUUID(), 10L, 1_000L,
                Instant.now().plusSeconds(600)
        );

        // when & then
        assertThatThrownBy(() ->
                subscriptionEventPublisher.publishFailed(
                        subscription,
                        UUID.randomUUID(),
                        UUID.randomUUID().toString()
                )
        ).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(outboxEventStore);
    }

    @Test
    @DisplayName("요청 수량이 최대 청약 수량 이하이면 한도 초과 이벤트를 저장하지 않는다")
    void rejectsLimitExceededEventWhenQuantityDoesNotExceedLimit() {
        assertThatThrownBy(() ->
                subscriptionEventPublisher.publishLimitExceeded(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        100L,
                        100L,
                        UUID.randomUUID().toString()
                )
        ).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(outboxEventStore);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private EventEnvelope<?> captureCompensationRequestedEnvelope() {
        ArgumentCaptor<EventEnvelope<?>> envelopeCaptor =
                ArgumentCaptor.forClass(
                        (Class) EventEnvelope.class
                );

        verify(outboxEventStore).save(
                eq("SUBSCRIPTION"),
                eq(COMPENSATION_REQUESTED_TOPIC),
                envelopeCaptor.capture()
        );

        return envelopeCaptor.getValue();
    }
}