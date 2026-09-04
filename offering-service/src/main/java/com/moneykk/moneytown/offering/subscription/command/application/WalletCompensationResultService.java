package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.global.processed.ProcessedEventService;
import com.moneykk.moneytown.offering.subscription.domain.entity.CompensationStatus;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionCompensation;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionCompensationRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.WalletCompensationResultPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletCompensationResultService {

    private static final String SUCCEEDED_EVENT_TYPE =
            "WalletCompensationSucceeded";

    private static final String FAILED_EVENT_TYPE =
            "WalletCompensationFailed";

    private final ProcessedEventService processedEventService;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionCompensationRepository
            subscriptionCompensationRepository;

    /**
     * Wallet 보상 성공 결과를 처리한다.
     *
     * @return 새로운 이벤트를 처리했으면 true,
     *         동일 eventId가 이미 처리됐으면 false
     */
    public boolean handleSucceeded(
            EventEnvelope<WalletCompensationResultPayload> envelope,
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
                () -> applyResult(subscriptionId, envelope, true)
        );
    }

    /**
     * Wallet 보상 실패 결과를 처리한다.
     *
     * 실패 결과를 기록하는 것 자체는 정상적인 이벤트 처리다.
     * 실제 보상 재요청은 별도 재처리 로직에서 수행한다.
     */
    public boolean handleFailed(
            EventEnvelope<WalletCompensationResultPayload> envelope,
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
                () -> applyResult(subscriptionId, envelope, false)
        );
    }

    /**
     * ProcessedEventService가 시작한 트랜잭션 안에서 실행된다.
     * 청약 → 보상 진행 행 순서로 잠금 조회한다.
     */
    private void applyResult(
            UUID subscriptionId,
            EventEnvelope<WalletCompensationResultPayload> envelope,
            boolean succeeded
    ) {
        Subscription subscription = subscriptionRepository
                .findByIdForUpdate(subscriptionId)
                .orElseThrow(() -> new BusinessException(
                        SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND
                ));

        if (!subscription.getUserId().equals(envelope.userId())) {
            throw new IllegalArgumentException(
                    "Wallet 보상 결과의 userId가 청약자와 일치하지 않습니다."
            );
        }

        SubscriptionCompensation compensation =
                subscriptionCompensationRepository
                        .findBySubscriptionIdForUpdate(subscriptionId)
                        .orElseThrow(() -> new IllegalStateException(
                                "보상 진행 정보가 없습니다. subscriptionId="
                                        + subscriptionId
                        ));

        WalletCompensationResultPayload payload = envelope.payload();

        // 실제 해제·환불 금액이 해당 청약의 금액과 일치하는지 확인한다.
        // NONE은 신규 금융 처리가 없어 amount가 null이다.
        if (succeeded
                && !"NONE".equals(payload.compensationType())
                && !Objects.equals(
                subscription.getAmount(),
                payload.amount()
        )) {
            throw new IllegalArgumentException(
                    "Wallet 보상 금액이 청약 금액과 일치하지 않습니다."
            );
        }

        /*
         * 다른 eventId로 성공이 재전달되거나 늦은 실패가 도착해도
         * 이미 반영한 Wallet 성공 상태는 유지한다.
         */
        if (compensation.getWalletStatus()
                == CompensationStatus.SUCCEEDED) {
            log.info(
                    "이미 Wallet 보상이 완료된 청약의 결과 수신. "
                            + "subscriptionId={}, eventId={}, eventType={}",
                    subscriptionId,
                    envelope.eventId(),
                    envelope.eventType()
            );
            return;
        }

        SubscriptionStatus status = subscription.getSubscriptionStatus();

        // 수동 확인 중에 늦게 도착한 결과도 기록하되,
        // 이 서비스에서는 청약 상태를 자동으로 변경하지 않는다.
        if (status != SubscriptionStatus.COMPENSATING
                && status != SubscriptionStatus.MANUAL_REVIEW) {
            throw new IllegalStateException(
                    "Wallet 보상 결과를 반영할 수 없는 청약 상태입니다. "
                            + "subscriptionId=" + subscriptionId
                            + ", status=" + status
            );
        }

        if (succeeded) {
            compensation.markWalletSucceeded();
        } else {
            compensation.markWalletFailed(payload.reason());

            log.warn(
                    "Wallet 보상 실패 결과 반영. "
                            + "subscriptionId={}, eventId={}, reason={}",
                    subscriptionId,
                    envelope.eventId(),
                    payload.reason()
            );
        }

        /*
         * 잠금 조회한 관리 엔티티이므로 dirty checking으로 저장된다.
         * 처리 이력과 보상 결과가 동일 트랜잭션으로 커밋된다.
         *
         * 수량 복원과 최종 CANCELLED 판정은
         * Holding 결과 처리와 함께 후속 단계에서 연결한다.
         */
    }

    private UUID validateEnvelope(
            EventEnvelope<WalletCompensationResultPayload> envelope,
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
            WalletCompensationResultPayload payload
    ) {
        requirePositive(payload.holdId(), "holdId");
        requirePositive(payload.walletId(), "walletId");

        String compensationType = payload.compensationType();

        if ("NONE".equals(compensationType)) {
            if (payload.transactionId() != null || payload.amount() != null) {
                throw new IllegalArgumentException(
                        "NONE 결과의 transactionId와 amount는 null이어야 합니다."
                );
            }
        } else if ("RELEASE".equals(compensationType)
                || "REFUND".equals(compensationType)) {
            requirePositive(payload.transactionId(), "transactionId");
            requirePositive(payload.amount(), "amount");
        } else {
            throw new IllegalArgumentException(
                    "compensationType은 RELEASE, REFUND, NONE 중 하나여야 합니다."
            );
        }

        if (payload.reason() != null) {
            throw new IllegalArgumentException(
                    "보상 성공 결과의 reason은 null이어야 합니다."
            );
        }
    }

    private void validateFailedPayload(
            WalletCompensationResultPayload payload
    ) {
        // HOLD_NOT_FOUND 등 조회 실패에서는 ID가 null일 수 있다.
        if (payload.holdId() != null) {
            requirePositive(payload.holdId(), "holdId");
        }

        if (payload.walletId() != null) {
            requirePositive(payload.walletId(), "walletId");
        }

        if (payload.reason() == null
                || payload.reason().isBlank()
                || payload.reason().length() > 100) {
            throw new IllegalArgumentException(
                    "reason은 비어 있을 수 없고 100자를 초과할 수 없습니다."
            );
        }

        if (payload.compensationType() != null
                || payload.transactionId() != null
                || payload.amount() != null) {
            throw new IllegalArgumentException(
                    "보상 실패 결과의 compensationType, transactionId, "
                            + "amount는 null이어야 합니다."
            );
        }
    }

    private static void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                    fieldName + "는 양수여야 합니다."
            );
        }
    }
}