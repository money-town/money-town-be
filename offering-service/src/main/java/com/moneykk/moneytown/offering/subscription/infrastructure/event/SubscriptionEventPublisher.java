package com.moneykk.moneytown.offering.subscription.infrastructure.event;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.offering.global.outbox.OutboxEventStore;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SubscriptionEventPublisher {

    private static final String AGGREGATE_TYPE = "SUBSCRIPTION";
    private static final String RESERVED_EVENT_TYPE = "SubscriptionReserved";
    private static final String RESERVED_TOPIC = "subscription-reserved";

    private static final String CONFIRMED_EVENT_TYPE = "SubscriptionConfirmed";
    private static final String CONFIRMED_TOPIC = "subscription-confirmed";

    private static final String COMPENSATION_REQUESTED_EVENT_TYPE = "SubscriptionCompensationRequested";
    private static final String COMPENSATION_REQUESTED_TOPIC = "subscription-compensation-requested";

    private final OutboxEventStore outboxEventStore;

    /**
     * 청약금 동결 요청 이벤트를 Outbox에 저장한다.
     *
     * 청약 생성과 동일한 트랜잭션 안에서 호출한다.
     */
    public void publishReserved(
            Subscription subscription,
            String correlationId
    ) {
        validateCommon(subscription, correlationId);

        if (subscription.getSubscriptionStatus()
                != SubscriptionStatus.PROCESSING) {
            throw new IllegalStateException(
                    "PROCESSING 청약만 동결 요청 이벤트를 생성할 수 있습니다."
            );
        }

        SubscriptionReservedPayload payload =
                new SubscriptionReservedPayload(
                        subscription.getAmount()
                );

        EventEnvelope<SubscriptionReservedPayload> envelope =
                EventEnvelope.of(
                        RESERVED_EVENT_TYPE,
                        subscription.getSubscriptionId().toString(),
                        subscription.getUserId(),
                        correlationId,
                        payload
                );

        outboxEventStore.save(
                AGGREGATE_TYPE,
                RESERVED_TOPIC,
                envelope
        );
    }

    /**
     * 청약 확정 이벤트를 Outbox에 저장한다.
     *
     * 청약 확정 및 수신 이벤트 처리 이력과
     * 동일한 트랜잭션 안에서 호출한다.
     *
     * @param assetId 해당 청약의 공모에 저장된 자산 ID
     * @param correlationId 수신한 동결 성공 이벤트의 correlationId
     */
    public void publishConfirmed(
            Subscription subscription,
            UUID assetId,
            String correlationId
    ) {
        validateCommon(subscription, correlationId);
        Objects.requireNonNull(assetId, "assetId는 필수입니다.");

        if (subscription.getSubscriptionStatus()
                != SubscriptionStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "CONFIRMED 청약만 청약 확정 이벤트를 생성할 수 있습니다."
            );
        }

        SubscriptionConfirmedPayload payload =
                new SubscriptionConfirmedPayload(
                        subscription.getOfferingId(),
                        assetId,
                        subscription.getQuantity()
                );

        EventEnvelope<SubscriptionConfirmedPayload> envelope =
                EventEnvelope.of(
                        CONFIRMED_EVENT_TYPE,
                        subscription.getSubscriptionId().toString(),
                        subscription.getUserId(),
                        correlationId,
                        payload
                );

        outboxEventStore.save(
                AGGREGATE_TYPE,
                CONFIRMED_TOPIC,
                envelope
        );
    }

    /**
     * 공모 중단 또는 모집 미달에 따른 보상 요청을 Outbox에 저장한다.
     *
     * 호출 서비스에서 청약을 COMPENSATING으로 전환하고,
     * 동일 트랜잭션 안에서 호출해야 한다.
     *
     * @param assetId 해당 청약의 공모에 저장된 자산 ID
     * @param correlationId 보상을 시작한 요청 또는 작업의 추적 ID
     */
    public void publishCompensationRequested(
            Subscription subscription,
            UUID assetId,
            String correlationId
    ) {
        validateCommon(subscription, correlationId);

        Objects.requireNonNull(
                subscription.getOfferingId(),
                "offeringId는 필수입니다."
        );
        Objects.requireNonNull(assetId, "assetId는 필수입니다.");

        if (subscription.getSubscriptionStatus()
                != SubscriptionStatus.COMPENSATING) {
            throw new IllegalStateException(
                    "COMPENSATING 청약만 보상 요청 이벤트를 생성할 수 있습니다."
            );
        }

        if (subscription.getCancellationType() == null) {
            throw new IllegalStateException(
                    "공모 중단 또는 모집 미달에 따른 취소 사유가 필요합니다."
            );
        }

        SubscriptionCompensationRequestedPayload payload =
                new SubscriptionCompensationRequestedPayload(
                        subscription.getOfferingId(),
                        assetId,
                        subscription.getCancellationType().name()
                );

        EventEnvelope<SubscriptionCompensationRequestedPayload> envelope =
                EventEnvelope.of(
                        COMPENSATION_REQUESTED_EVENT_TYPE,
                        subscription.getSubscriptionId().toString(),
                        subscription.getUserId(),
                        correlationId,
                        payload
                );

        outboxEventStore.save(
                AGGREGATE_TYPE,
                COMPENSATION_REQUESTED_TOPIC,
                envelope
        );
    }

    private void validateCommon(
            Subscription subscription,
            String correlationId
    ) {
        Objects.requireNonNull(subscription, "subscription은 필수입니다.");
        Objects.requireNonNull(
                subscription.getSubscriptionId(),
                "subscriptionId는 필수입니다."
        );
        Objects.requireNonNull(
                subscription.getUserId(),
                "userId는 필수입니다."
        );

        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException(
                    "correlationId는 필수입니다."
            );
        }
    }
}