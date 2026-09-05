package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.global.processed.ProcessedEventService;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.HoldingAllocationStatus;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.HoldingAllocationFailedPayload;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.HoldingAllocationSucceededPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldingAllocationResultService {

    private static final String SUCCEEDED_EVENT_TYPE =
            "HoldingAllocationSucceeded";

    private static final String FAILED_EVENT_TYPE =
            "HoldingAllocationFailed";

    private final ProcessedEventService processedEventService;
    private final SubscriptionRepository subscriptionRepository;
    private final OfferingRepository offeringRepository;

    /**
     * 처리 이력과 Holding 지분 배정 성공 상태를
     * 같은 트랜잭션으로 저장한다.
     *
     * @return 신규 이벤트를 처리했으면 true,
     *         같은 Consumer Group에서 이미 처리한 이벤트면 false
     */
    public boolean handleSucceeded(
            EventEnvelope<HoldingAllocationSucceededPayload> envelope,
            String consumerGroup
    ) {
        UUID subscriptionId = validateEnvelope(
                envelope,
                SUCCEEDED_EVENT_TYPE
        );

        validateSucceededPayload(envelope.payload());

        return processedEventService.processOnce(
                envelope,
                consumerGroup,
                () -> applySucceeded(subscriptionId, envelope)
        );
    }

    /**
     * 처리 이력과 Holding 지분 배정 실패 상태를
     * 같은 트랜잭션으로 저장한다.
     *
     * 배정 실패를 받더라도 청약의 CONFIRMED 상태는 유지한다.
     */
    public boolean handleFailed(
            EventEnvelope<HoldingAllocationFailedPayload> envelope,
            String consumerGroup
    ) {
        UUID subscriptionId = validateEnvelope(
                envelope,
                FAILED_EVENT_TYPE
        );

        validateFailedPayload(envelope.payload());

        return processedEventService.processOnce(
                envelope,
                consumerGroup,
                () -> applyFailed(subscriptionId, envelope)
        );
    }

    private void applySucceeded(
            UUID subscriptionId,
            EventEnvelope<HoldingAllocationSucceededPayload> envelope
    ) {
        HoldingAllocationSucceededPayload payload =
                envelope.payload();

        Subscription subscription = loadSubscription(
                subscriptionId,
                envelope.userId(),
                payload.assetId()
        );

        if (!Objects.equals(
                subscription.getQuantity(),
                payload.quantity()
        )) {
            throw new IllegalArgumentException(
                    "Holding 배정 수량이 청약 수량과 일치하지 않습니다."
            );
        }

        /*
         * 같은 청약에 다른 eventId의 성공 결과가 다시 도착해도
         * 배정 상태를 중복 변경하지 않는다.
         */
        if (subscription.getHoldingAllocationStatus()
                == HoldingAllocationStatus.SUCCEEDED) {
            log.info(
                    "이미 Holding 배정이 완료된 청약의 성공 결과 수신. "
                            + "subscriptionId={}, eventId={}, result={}",
                    subscriptionId,
                    envelope.eventId(),
                    payload.result()
            );
            return;
        }

        validateAllocationStarted(subscription);

        subscription.markHoldingAllocationSucceeded();

        /*
         * 공모 취소와 경합하여 청약 상태가 이미 변경된 경우에도
         * 수신된 배정 결과는 기록한다.
         *
         * 자동 복구나 수동 재처리는 다음 PR에서 처리한다.
         */
        if (subscription.getSubscriptionStatus()
                != SubscriptionStatus.CONFIRMED) {
            log.error(
                    "청약 확정 이후 상태에서 늦은 Holding 배정 성공 수신. "
                            + "subscriptionId={}, eventId={}, status={}",
                    subscriptionId,
                    envelope.eventId(),
                    subscription.getSubscriptionStatus()
            );
            return;
        }

        log.info(
                "Holding 배정 성공 결과 반영. "
                        + "subscriptionId={}, eventId={}, "
                        + "holdingId={}, result={}",
                subscriptionId,
                envelope.eventId(),
                payload.holdingId(),
                payload.result()
        );
    }

    private void applyFailed(
            UUID subscriptionId,
            EventEnvelope<HoldingAllocationFailedPayload> envelope
    ) {
        HoldingAllocationFailedPayload payload =
                envelope.payload();

        Subscription subscription = loadSubscription(
                subscriptionId,
                envelope.userId(),
                payload.assetId()
        );

        /*
         * 성공 이후 다른 eventId의 늦은 실패가 도착하더라도
         * SUCCEEDED 상태를 FAILED로 되돌리지 않는다.
         */
        if (subscription.getHoldingAllocationStatus()
                == HoldingAllocationStatus.SUCCEEDED) {
            log.info(
                    "Holding 배정 성공 후 늦은 실패 결과 수신. "
                            + "subscriptionId={}, eventId={}, errorCode={}",
                    subscriptionId,
                    envelope.eventId(),
                    payload.errorCode()
            );
            return;
        }

        validateAllocationStarted(subscription);

        subscription.markHoldingAllocationFailed(
                payload.errorCode()
        );

        log.warn(
                "Holding 배정 실패 결과 반영. "
                        + "subscriptionId={}, eventId={}, status={}, "
                        + "errorCode={}, errorMessage={}, retryable={}",
                subscriptionId,
                envelope.eventId(),
                subscription.getSubscriptionStatus(),
                payload.errorCode(),
                payload.errorMessage(),
                payload.retryable()
        );
    }

    /**
     * ProcessedEventService의 트랜잭션 안에서
     * 공모, 청약 순서로 잠금을 획득한다.
     */
    private Subscription loadSubscription(
            UUID subscriptionId,
            UUID userId,
            UUID assetId
    ) {
        UUID offeringId = subscriptionRepository
                .findOfferingIdBySubscriptionId(subscriptionId)
                .orElseThrow(() -> new BusinessException(
                        SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND
                ));

        Offering offering = offeringRepository
                .findByIdForUpdate(offeringId)
                .orElseThrow(() -> new BusinessException(
                        OfferingErrorCode.OFFERING_NOT_FOUND
                ));

        Subscription subscription = subscriptionRepository
                .findByIdForUpdate(subscriptionId)
                .orElseThrow(() -> new BusinessException(
                        SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND
                ));

        if (!subscription.getUserId().equals(userId)) {
            throw new IllegalArgumentException(
                    "Holding 배정 결과의 userId가 청약자와 일치하지 않습니다."
            );
        }

        if (!Objects.equals(
                offering.getAssetId(),
                assetId
        )) {
            throw new IllegalArgumentException(
                    "Holding 배정 결과의 assetId가 공모 자산과 일치하지 않습니다."
            );
        }

        return subscription;
    }

    /**
     * 청약 확정과 함께 지분 배정 요청이 시작됐는지 확인한다.
     */
    private void validateAllocationStarted(
            Subscription subscription
    ) {
        if (subscription.getHoldingAllocationStatus() == null) {
            throw new IllegalStateException(
                    "지분 배정을 요청하지 않은 청약의 결과입니다. "
                            + "subscriptionId="
                            + subscription.getSubscriptionId()
            );
        }
    }

    private UUID validateEnvelope(
            EventEnvelope<?> envelope,
            String expectedEventType
    ) {
        Objects.requireNonNull(
                envelope,
                "envelope은 필수입니다."
        );
        Objects.requireNonNull(
                envelope.eventId(),
                "eventId는 필수입니다."
        );
        Objects.requireNonNull(
                envelope.userId(),
                "userId는 필수입니다."
        );
        Objects.requireNonNull(
                envelope.occurredAt(),
                "occurredAt은 필수입니다."
        );
        Objects.requireNonNull(
                envelope.payload(),
                "payload는 필수입니다."
        );

        if (!expectedEventType.equals(envelope.eventType())) {
            throw new IllegalArgumentException(
                    expectedEventType
                            + " 이벤트만 처리할 수 있습니다."
            );
        }

        if (envelope.correlationId() == null
                || envelope.correlationId().isBlank()) {
            throw new IllegalArgumentException(
                    "correlationId는 필수입니다."
            );
        }

        String aggregateId = envelope.aggregateId();

        if (aggregateId == null || aggregateId.isBlank()) {
            throw new IllegalArgumentException(
                    "aggregateId는 필수입니다."
            );
        }

        try {
            return UUID.fromString(aggregateId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "aggregateId는 UUID 형식의 "
                            + "subscriptionId여야 합니다.",
                    e
            );
        }
    }

    private void validateSucceededPayload(
            HoldingAllocationSucceededPayload payload
    ) {
        Objects.requireNonNull(
                payload.assetId(),
                "assetId는 필수입니다."
        );
        Objects.requireNonNull(
                payload.holdingId(),
                "holdingId는 필수입니다."
        );

        if (payload.quantity() == null
                || payload.quantity() <= 0) {
            throw new IllegalArgumentException(
                    "quantity는 양수여야 합니다."
            );
        }

        if (!"ALLOCATED".equals(payload.result())
                && !"ALREADY_PROCESSED".equals(
                payload.result()
        )) {
            throw new IllegalArgumentException(
                    "result는 ALLOCATED 또는 "
                            + "ALREADY_PROCESSED여야 합니다."
            );
        }
    }

    private void validateFailedPayload(
            HoldingAllocationFailedPayload payload
    ) {
        Objects.requireNonNull(
                payload.assetId(),
                "assetId는 필수입니다."
        );
        Objects.requireNonNull(
                payload.retryable(),
                "retryable은 필수입니다."
        );

        if (payload.errorCode() == null
                || payload.errorCode().isBlank()
                || payload.errorCode().length() > 100) {
            throw new IllegalArgumentException(
                    "errorCode는 비어 있을 수 없고 "
                            + "100자를 초과할 수 없습니다."
            );
        }
    }
}