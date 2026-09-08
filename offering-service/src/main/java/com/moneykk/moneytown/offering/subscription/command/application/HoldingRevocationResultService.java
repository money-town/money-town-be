package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.global.processed.ProcessedEventService;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.CompensationStatus;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionCompensation;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionCompensationRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.HoldingRevocationFailedPayload;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.HoldingRevocationSucceededPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldingRevocationResultService {

    private static final String SUCCEEDED_EVENT_TYPE =
            "HoldingRevocationSucceeded";

    private static final String FAILED_EVENT_TYPE =
            "HoldingRevocationFailed";

    private final ProcessedEventService processedEventService;
    private final SubscriptionRepository subscriptionRepository;
    private final OfferingRepository offeringRepository;
    private final SubscriptionCompensationRepository subscriptionCompensationRepository;
    private final SubscriptionCompensationCompletionService subscriptionCompensationCompletionService;

    /**
     * 수신 처리 이력과 Holding 회수 성공 상태를 같은 트랜잭션으로 저장한다.
     *
     * @return 신규 이벤트를 처리했으면 true,
     *         같은 Consumer Group에서 이미 처리한 eventId이면 false
     */
    public boolean handleSucceeded(
            EventEnvelope<HoldingRevocationSucceededPayload> envelope,
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
     * 실패 결과와 처리 이력을 저장한다.
     * 업무 실패 결과를 받았다는 이유만으로 예외를 던지거나 재요청하지 않는다.
     */
    public boolean handleFailed(
            EventEnvelope<HoldingRevocationFailedPayload> envelope,
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
            EventEnvelope<HoldingRevocationSucceededPayload> envelope
    ) {
        HoldingRevocationSucceededPayload payload = envelope.payload();

        ResultContext context = loadContext(
                subscriptionId,
                envelope.userId(),
                payload.assetId()
        );

        // NO_ACTION의 quantity는 0이므로 실제 회수 수량 비교에서 제외한다.
        if ("REVOKED".equals(payload.result())
                && !Objects.equals(
                context.subscription().getQuantity(),
                payload.quantity()
        )) {
            throw new IllegalArgumentException(
                    "Holding 회수 수량이 청약 수량과 일치하지 않습니다."
            );
        }

        if (isAlreadySucceeded(context.compensation(), envelope)) {
            subscriptionCompensationCompletionService.completeIfReady(
                    subscriptionId
            );
            return;
        }

        validateSubscriptionStatus(context.subscription());

        context.compensation().markHoldingSucceeded();

        subscriptionCompensationCompletionService.completeIfReady(
                subscriptionId
        );

        log.info(
                "Holding 회수 성공 결과 반영. "
                        + "subscriptionId={}, eventId={}, result={}, "
                        + "noActionReason={}",
                subscriptionId,
                envelope.eventId(),
                payload.result(),
                payload.noActionReason()
        );
    }

    private void applyFailed(
            UUID subscriptionId,
            EventEnvelope<HoldingRevocationFailedPayload> envelope
    ) {
        HoldingRevocationFailedPayload payload = envelope.payload();

        ResultContext context = loadContext(
                subscriptionId,
                envelope.userId(),
                payload.assetId()
        );

        if (isAlreadySucceeded(context.compensation(), envelope)) {
            return;
        }

        validateSubscriptionStatus(context.subscription());

        context.compensation().markHoldingFailed(payload.errorCode());

        // DB에는 errorCode만 저장한다. 재처리 판단과 실행은 후속 기능이다.
        log.warn(
                "Holding 회수 실패 결과 반영. "
                        + "subscriptionId={}, eventId={}, errorCode={}, "
                        + "errorMessage={}, retryable={}",
                subscriptionId,
                envelope.eventId(),
                payload.errorCode(),
                payload.errorMessage(),
                payload.retryable()
        );
    }

    /**
     * processOnce()의 트랜잭션 안에서 공모 → 청약 → 보상 진행 행 순서로 잠근다.
     * 수신 userId와 assetId가 실제 청약의 대상과 일치하는지 확인한다.
     */
    private ResultContext loadContext(
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
                    "Holding 회수 결과의 userId가 청약자와 일치하지 않습니다."
            );
        }

        if (!Objects.equals(offering.getAssetId(), assetId)) {
            throw new IllegalArgumentException(
                    "Holding 회수 결과의 assetId가 공모 자산과 일치하지 않습니다."
            );
        }

        SubscriptionCompensation compensation =
                subscriptionCompensationRepository
                        .findBySubscriptionIdForUpdate(subscriptionId)
                        .orElseThrow(() -> new IllegalStateException(
                                "보상 진행 정보가 없습니다. subscriptionId="
                                        + subscriptionId
                        ));

        return new ResultContext(subscription, compensation);
    }

    /**
     * 다른 eventId로 성공이 재전달되거나 늦은 실패가 도착해도
     * 이미 기록한 Holding 회수 성공을 되돌리지 않는다.
     */
    private boolean isAlreadySucceeded(
            SubscriptionCompensation compensation,
            EventEnvelope<?> envelope
    ) {
        if (compensation.getHoldingStatus()
                != CompensationStatus.SUCCEEDED) {
            return false;
        }

        log.info(
                "이미 Holding 회수가 완료된 청약의 결과 수신. "
                        + "subscriptionId={}, eventId={}, eventType={}",
                compensation.getSubscriptionId(),
                envelope.eventId(),
                envelope.eventType()
        );
        return true;
    }

    private void validateSubscriptionStatus(Subscription subscription) {
        SubscriptionStatus status = subscription.getSubscriptionStatus();

        // 수동 확인 중 도착한 결과도 저장하되 청약 상태는 유지한다.
        if (status != SubscriptionStatus.COMPENSATING
                && status != SubscriptionStatus.MANUAL_REVIEW) {
            throw new IllegalStateException(
                    "Holding 회수 결과를 반영할 수 없는 청약 상태입니다. "
                            + "subscriptionId="
                            + subscription.getSubscriptionId()
                            + ", status=" + status
            );
        }
    }

    private UUID validateEnvelope(
            EventEnvelope<?> envelope,
            String expectedEventType
    ) {
        Objects.requireNonNull(envelope, "envelope은 필수입니다.");
        Objects.requireNonNull(envelope.eventId(), "eventId는 필수입니다.");
        Objects.requireNonNull(envelope.userId(), "userId는 필수입니다.");
        Objects.requireNonNull(envelope.occurredAt(), "occurredAt은 필수입니다.");
        Objects.requireNonNull(envelope.payload(), "payload는 필수입니다.");

        if (!expectedEventType.equals(envelope.eventType())) {
            throw new IllegalArgumentException(
                    expectedEventType + " 이벤트만 처리할 수 있습니다."
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
                    "aggregateId는 UUID 형식의 subscriptionId여야 합니다.",
                    e
            );
        }
    }

    private void validateSucceededPayload(
            HoldingRevocationSucceededPayload payload
    ) {
        Objects.requireNonNull(payload.assetId(), "assetId는 필수입니다.");

        if ("REVOKED".equals(payload.result())) {
            Objects.requireNonNull(
                    payload.holdingId(),
                    "REVOKED 결과의 holdingId는 필수입니다."
            );

            if (payload.quantity() == null || payload.quantity() <= 0) {
                throw new IllegalArgumentException(
                        "REVOKED 결과의 quantity는 양수여야 합니다."
                );
            }

            if (payload.noActionReason() != null) {
                throw new IllegalArgumentException(
                        "REVOKED 결과의 noActionReason은 null이어야 합니다."
                );
            }
            return;
        }

        if ("NO_ACTION".equals(payload.result())) {
            if (!Long.valueOf(0L).equals(payload.quantity())) {
                throw new IllegalArgumentException(
                        "NO_ACTION 결과의 quantity는 0이어야 합니다."
                );
            }

            String reason = payload.noActionReason();

            if (!"NOT_ALLOCATED".equals(reason)
                    && !"ALREADY_REVOKED".equals(reason)) {
                throw new IllegalArgumentException(
                        "NO_ACTION 결과의 noActionReason은 "
                                + "NOT_ALLOCATED 또는 ALREADY_REVOKED여야 합니다."
                );
            }
            return;
        }

        throw new IllegalArgumentException(
                "result는 REVOKED 또는 NO_ACTION이어야 합니다."
        );
    }

    private void validateFailedPayload(
            HoldingRevocationFailedPayload payload
    ) {
        Objects.requireNonNull(payload.assetId(), "assetId는 필수입니다.");

        if (payload.errorCode() == null
                || payload.errorCode().isBlank()
                || payload.errorCode().length() > 100) {
            throw new IllegalArgumentException(
                    "errorCode는 비어 있을 수 없고 100자를 초과할 수 없습니다."
            );
        }
    }

    private record ResultContext(
            Subscription subscription,
            SubscriptionCompensation compensation
    ) {
    }
}