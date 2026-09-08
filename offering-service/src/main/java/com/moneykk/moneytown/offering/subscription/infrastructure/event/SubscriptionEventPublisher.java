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
    private static final String SUBSCRIPTION_REQUEST_AGGREGATE_TYPE = "SUBSCRIPTION_REQUEST";

    private static final String RESERVED_EVENT_TYPE = "SubscriptionReserved";
    private static final String RESERVED_TOPIC = "subscription-reserved";

    private static final String CONFIRMED_EVENT_TYPE = "SubscriptionConfirmed";
    private static final String CONFIRMED_TOPIC = "subscription-confirmed";

    private static final String COMPENSATION_REQUESTED_EVENT_TYPE = "SubscriptionCompensationRequested";
    private static final String COMPENSATION_REQUESTED_TOPIC = "subscription-compensation-requested";
    private static final String RESERVATION_EXPIRED_REASON = "RESERVATION_EXPIRED";

    private static final String LIMIT_EXCEEDED_EVENT_TYPE = "SubscriptionLimitExceeded";
    private static final String FAILED_EVENT_TYPE = "SubscriptionFailed";
    private static final String POST_FDS_TOPIC = "subscription-events";

    private final OutboxEventStore outboxEventStore;

    /*
     * TODO: Analysis 담당자 반영 확인
     * - 외부 eventType은 SubscriptionFailed,
     *   SubscriptionLimitExceeded 형식 사용
     * - Analysis EventType.fromEventName() 매핑 추가
     * - requestedQuantity, maxSubscriptionQuantity,
     *   failureCode 수신 Payload 반영
     */

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
     * 공모 중단, 모집 미달 또는 청약 예약 만료에 따른
     * 보상 요청을 Outbox에 저장한다.
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

        String compensationReason =
                resolveCompensationReason(subscription);

        SubscriptionCompensationRequestedPayload payload =
                new SubscriptionCompensationRequestedPayload(
                        subscription.getOfferingId(),
                        assetId,
                        compensationReason
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

    /**
     * 사용자가 공모의 1인당 최대 청약 수량을 초과하여 요청한 경우
     * PostFDS 집계를 위한 이벤트를 Outbox에 저장한다.
     *
     * 청약 엔티티 생성 전에 발생하는 이벤트이므로
     * subscriptionId는 Payload에 null로 전달한다.
     *
     * Outbox의 aggregateId는 null일 수 없으므로
     * 청약 요청을 선점할 때 생성한 idempotencyRequestId를 사용한다.
     */
    public void publishLimitExceeded(
            UUID idempotencyRequestId,
            UUID userId,
            UUID assetId,
            Long requestedQuantity,
            Long maxSubscriptionQuantity,
            String correlationId
    ) {
        validateLimitExceeded(
                idempotencyRequestId,
                userId,
                assetId,
                requestedQuantity,
                maxSubscriptionQuantity,
                correlationId
        );

        SubscriptionLimitExceededPayload payload =
                new SubscriptionLimitExceededPayload(
                        userId,
                        assetId,
                        null,
                        requestedQuantity,
                        maxSubscriptionQuantity
                );

        EventEnvelope<SubscriptionLimitExceededPayload> envelope =
                EventEnvelope.of(
                        LIMIT_EXCEEDED_EVENT_TYPE,
                        idempotencyRequestId.toString(),
                        userId,
                        correlationId,
                        payload
                );

        outboxEventStore.save(
                SUBSCRIPTION_REQUEST_AGGREGATE_TYPE,
                POST_FDS_TOPIC,
                envelope
        );
    }


    /**
     * Wallet HOLD 실패 처리와 공모 수량 복원이 완료되어
     * 최종 REJECTED 상태가 된 청약의 실패 이벤트를 Outbox에 저장한다.
     *
     * 청약 상태 변경, 공모 수량 복원 및 수신 이벤트 처리 이력과
     * 동일한 트랜잭션 안에서 호출해야 한다.
     *
     * @param subscription 최종 거절된 청약
     * @param assetId 청약 대상 공모의 자산 ID
     * @param correlationId Wallet HOLD 요청부터 이어진 추적 ID
     */
    public void publishFailed(
            Subscription subscription,
            UUID assetId,
            String correlationId
    ) {
        validateCommon(subscription, correlationId);

        Objects.requireNonNull(
                assetId,
                "assetId는 필수입니다."
        );

        if (subscription.getSubscriptionStatus()
                != SubscriptionStatus.REJECTED) {
            throw new IllegalStateException(
                    "REJECTED 청약만 실패 이벤트를 생성할 수 있습니다."
            );
        }

        String failureCode = subscription.getFailureCode();

        if (failureCode == null
                || failureCode.isBlank()
                || failureCode.length() > 50) {
            throw new IllegalStateException(
                    "failureCode는 필수이며 50자를 초과할 수 없습니다."
            );
        }

        SubscriptionFailedPayload payload =
                new SubscriptionFailedPayload(
                        subscription.getUserId(),
                        assetId,
                        subscription.getSubscriptionId(),
                        failureCode
                );

        EventEnvelope<SubscriptionFailedPayload> envelope =
                EventEnvelope.of(
                        FAILED_EVENT_TYPE,
                        subscription.getSubscriptionId().toString(),
                        subscription.getUserId(),
                        correlationId,
                        payload
                );

        outboxEventStore.save(
                AGGREGATE_TYPE,
                POST_FDS_TOPIC,
                envelope
        );
    }

    /**
     * 청약 상태에 따라 외부 서비스에 전달할 보상 사유를 결정한다.
     *
     * 공모 중단·모집 미달 보상은 cancellationType을 사용하고,
     * 예약 만료 보상은 failureCode의 RESERVATION_EXPIRED를 사용한다.
     */
    private String resolveCompensationReason(
            Subscription subscription
    ) {
        if (subscription.getCancellationType() != null) {
            return subscription.getCancellationType().name();
        }

        if (RESERVATION_EXPIRED_REASON.equals(
                subscription.getFailureCode()
        )) {
            return RESERVATION_EXPIRED_REASON;
        }

        throw new IllegalStateException(
                "보상 요청을 발행할 수 있는 사유가 없습니다."
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

    private void validateLimitExceeded(
            UUID idempotencyRequestId,
            UUID userId,
            UUID assetId,
            Long requestedQuantity,
            Long maxSubscriptionQuantity,
            String correlationId
    ) {
        Objects.requireNonNull(
                idempotencyRequestId,
                "idempotencyRequestId는 필수입니다."
        );
        Objects.requireNonNull(
                userId,
                "userId는 필수입니다."
        );
        Objects.requireNonNull(
                assetId,
                "assetId는 필수입니다."
        );
        Objects.requireNonNull(
                requestedQuantity,
                "requestedQuantity는 필수입니다."
        );
        Objects.requireNonNull(
                maxSubscriptionQuantity,
                "maxSubscriptionQuantity는 필수입니다."
        );

        if (requestedQuantity <= maxSubscriptionQuantity) {
            throw new IllegalArgumentException(
                    "요청 수량은 최대 청약 수량보다 커야 합니다."
            );
        }

        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException(
                    "correlationId는 필수입니다."
            );
        }
    }
}