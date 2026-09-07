package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.config.JpaAuditingConfig;
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
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.WalletHoldFailedPayload;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.WalletHoldSucceededPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletHoldResultService {

    private static final String SUCCEEDED_EVENT_TYPE = "WalletHoldSucceeded";

    private final ProcessedEventService processedEventService;
    private final SubscriptionRepository subscriptionRepository;
    private final OfferingRepository offeringRepository;
    private final SubscriptionEventPublisher subscriptionEventPublisher;
    private final SubscriptionCompensationRepository subscriptionCompensationRepository;

    /**
     * 동결 성공 이벤트를 처리한다.
     *
     * ProcessedEventService가 트랜잭션을 시작하고,
     * 청약 변경과 후속 Outbox 저장까지 같은 트랜잭션에서 실행한다.
     *
     * @param consumerGroup 실제 Kafka Listener가 사용하는 Consumer Group
     * @return 새로운 수신 이벤트를 처리했으면 true,
     *         동일 eventId가 이미 처리됐으면 false
     */
    public boolean handleSucceeded(
            EventEnvelope<WalletHoldSucceededPayload> envelope,
            String consumerGroup
    ) {
        validateSucceededEvent(envelope);

        UUID subscriptionId = parseSubscriptionId(envelope.aggregateId());

        return processedEventService.processOnce(
                envelope,
                consumerGroup,
                () -> confirmSubscription(subscriptionId, envelope)
        );
    }

    private void confirmSubscription(
            UUID subscriptionId,
            EventEnvelope<WalletHoldSucceededPayload> envelope
    ) {
        Subscription subscription = subscriptionRepository
                .findByIdForUpdate(subscriptionId)
                .orElseThrow(() -> new BusinessException(
                        SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND
                ));

        // 수신 이벤트가 실제 청약자의 지갑 처리 결과인지 확인한다.
        if (!subscription.getUserId().equals(envelope.userId())) {
            throw new IllegalArgumentException(
                    "동결 성공 이벤트의 userId가 청약자와 일치하지 않습니다."
            );
        }

        /*
         * 서로 다른 eventId로 같은 청약의 성공 결과가 도착하더라도
         * 이미 확정된 청약은 다시 확정하거나 이벤트를 다시 생성하지 않는다.
         */
        if (subscription.getSubscriptionStatus()
                == SubscriptionStatus.CONFIRMED) {
            log.info(
                    "이미 확정된 청약의 동결 성공 이벤트. "
                            + "subscriptionId={}, eventId={}",
                    subscriptionId,
                    envelope.eventId()
            );
            return;
        }

        /*
         * 보상 중이거나 종료된 청약을 다시 확정하지 않는다.
         * 현재 보상 상태에 따라 재요청 또는 수동 확인으로 연결한다.
         */
        if (subscription.getSubscriptionStatus()
                != SubscriptionStatus.PROCESSING) {
            handleLateHoldSucceeded(subscription, envelope);
            return;
        }

        /*
         * 청약이 참조하는 공모에서 assetId를 가져온다.
         * 수신 Payload나 클라이언트 입력을 assetId로 사용하지 않는다.
         */
        Offering offering = offeringRepository
                .findById(subscription.getOfferingId())
                .orElseThrow(() -> new BusinessException(
                        OfferingErrorCode.OFFERING_NOT_FOUND
                ));

        subscription.confirm(Instant.now());

        subscriptionEventPublisher.publishConfirmed(
                subscription,
                offering.getAssetId(),
                envelope.correlationId()
        );
    }

    private void validateSucceededEvent(
            EventEnvelope<WalletHoldSucceededPayload> envelope
    ) {
        Objects.requireNonNull(envelope, "envelope은 필수입니다.");
        Objects.requireNonNull(envelope.eventId(), "eventId는 필수입니다.");
        Objects.requireNonNull(envelope.userId(), "userId는 필수입니다.");
        Objects.requireNonNull(envelope.occurredAt(), "occurredAt은 필수입니다.");

        if (!SUCCEEDED_EVENT_TYPE.equals(envelope.eventType())) {
            throw new IllegalArgumentException(
                    "WalletHoldSucceeded 이벤트만 처리할 수 있습니다."
            );
        }

        if (envelope.correlationId() == null
                || envelope.correlationId().isBlank()) {
            throw new IllegalArgumentException(
                    "correlationId는 필수입니다."
            );
        }

        WalletHoldSucceededPayload payload = Objects.requireNonNull(
                envelope.payload(),
                "payload는 필수입니다."
        );

        if (payload.holdId() == null || payload.holdId() <= 0) {
            throw new IllegalArgumentException(
                    "holdId는 양수여야 합니다."
            );
        }

        if (payload.walletId() == null || payload.walletId() <= 0) {
            throw new IllegalArgumentException(
                    "walletId는 양수여야 합니다."
            );
        }

        if (!"HELD".equals(payload.status())) {
            throw new IllegalArgumentException(
                    "동결 성공 이벤트의 status는 HELD여야 합니다."
            );
        }
    }

    /**
     * 동결 실패 이벤트를 처리한다.
     *
     * 처리 이력, 공모 수량 복원, 청약 거절을
     * ProcessedEventService가 시작한 동일 트랜잭션에서 처리한다.
     */
    public boolean handleFailed(
            EventEnvelope<WalletHoldFailedPayload> envelope,
            String consumerGroup
    ) {
        validateFailedEvent(envelope);

        UUID subscriptionId = parseSubscriptionId(envelope.aggregateId());

        return processedEventService.processOnce(
                envelope,
                consumerGroup,
                () -> rejectSubscription(subscriptionId, envelope)
        );
    }

    /**
     * PROCESSING 청약은 수량 복원 후 거절한다.
     * 그 외 상태의 늦은 실패 결과는 상태와 수량을 유지한다.
     */
    private void rejectSubscription(
            UUID subscriptionId,
            EventEnvelope<WalletHoldFailedPayload> envelope
    ) {
        UUID offeringId = subscriptionRepository
                .findOfferingIdBySubscriptionId(subscriptionId)
                .orElseThrow(() -> new BusinessException(
                        SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND
                ));

        // 모집 미달 처리와 동일하게 공모 → 청약 순서로 잠근다.
        offeringRepository.findByIdForUpdate(offeringId)
                .orElseThrow(() -> new BusinessException(
                        OfferingErrorCode.OFFERING_NOT_FOUND
                ));

        Subscription subscription = subscriptionRepository
                .findByIdForUpdate(subscriptionId)
                .orElseThrow(() -> new BusinessException(
                        SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND
                ));

        if (!subscription.getUserId().equals(envelope.userId())) {
            throw new IllegalArgumentException(
                    "동결 실패 이벤트의 userId가 청약자와 일치하지 않습니다."
            );
        }

        /*
         * 다른 eventId로 동일한 실패 결과가 다시 도착해도
         * 이미 복원한 수량을 다시 증가시키지 않는다.
         */
        if (subscription.getSubscriptionStatus()
                == SubscriptionStatus.REJECTED
                && !subscription.isQuantityReserved()) {
            log.info(
                    "이미 거절된 청약의 동결 실패 이벤트. "
                            + "subscriptionId={}, eventId={}",
                    subscriptionId,
                    envelope.eventId()
            );
            return;
        }

        // 예외 없이 반환하여 늦은 결과도 처리 이력에 기록한다.
        if (subscription.getSubscriptionStatus()
                != SubscriptionStatus.PROCESSING) {
            log.warn(
                    "늦은 동결 실패 수신. 현재 청약 상태 유지. "
                            + "subscriptionId={}, eventId={}, status={}, reason={}",
                    subscriptionId,
                    envelope.eventId(),
                    subscription.getSubscriptionStatus(),
                    envelope.payload().reason()
            );
            return;
        }

        subscription.startHoldFailureCompensation(
                envelope.payload().reason()
        );

        int restoredRows = offeringRepository.restoreQuantity(
                offeringId,
                subscription.getQuantity(),
                JpaAuditingConfig.SYSTEM_USER_ID
        );

        if (restoredRows != 1) {
            throw new IllegalStateException(
                    "동결 실패에 따른 공모 수량 복원에 실패했습니다. "
                            + "subscriptionId=" + subscriptionId
                            + ", offeringId=" + offeringId
            );
        }

        subscription.completeHoldFailureRejection();
    }

    private void validateFailedEvent(
            EventEnvelope<WalletHoldFailedPayload> envelope
    ) {
        Objects.requireNonNull(envelope, "envelope은 필수입니다.");
        Objects.requireNonNull(envelope.eventId(), "eventId는 필수입니다.");
        Objects.requireNonNull(envelope.userId(), "userId는 필수입니다.");
        Objects.requireNonNull(envelope.occurredAt(), "occurredAt은 필수입니다.");

        if (!"WalletHoldFailed".equals(envelope.eventType())) {
            throw new IllegalArgumentException(
                    "WalletHoldFailed 이벤트만 처리할 수 있습니다."
            );
        }

        if (envelope.correlationId() == null
                || envelope.correlationId().isBlank()) {
            throw new IllegalArgumentException(
                    "correlationId는 필수입니다."
            );
        }

        WalletHoldFailedPayload payload = Objects.requireNonNull(
                envelope.payload(),
                "payload는 필수입니다."
        );

        if (!"FAILED".equals(payload.status())) {
            throw new IllegalArgumentException(
                    "동결 실패 이벤트의 status는 FAILED여야 합니다."
            );
        }

        if (payload.reason() == null
                || payload.reason().isBlank()
                || payload.reason().length() > 50) {
            throw new IllegalArgumentException(
                    "reason은 필수이며 50자를 초과할 수 없습니다."
            );
        }

        // 지갑이 존재하지 않는 실패는 walletId가 없을 수 있다.
        if (payload.walletId() != null && payload.walletId() <= 0) {
            throw new IllegalArgumentException(
                    "walletId가 있으면 양수여야 합니다."
            );
        }
    }

    /**
     * 청약 잠금을 획득한 상태에서 호출한다.
     * 처리 이력, 수동 확인 상태 변경 또는 보상 Outbox 저장이
     * 동일 트랜잭션으로 커밋된다.
     */
    private void handleLateHoldSucceeded(
            Subscription subscription,
            EventEnvelope<WalletHoldSucceededPayload> envelope
    ) {
        UUID subscriptionId = subscription.getSubscriptionId();

        SubscriptionCompensation compensation =
                subscriptionCompensationRepository
                        .findBySubscriptionIdForUpdate(subscriptionId)
                        .orElse(null);

        /*
         * 해당 청약의 Wallet 보상 성공이 이미 기록됐다면,
         * 늦게 도착한 과거 HOLD 결과로 보상 상태를 되돌리지 않는다.
         */
        if (compensation != null
                && compensation.getWalletStatus()
                == CompensationStatus.SUCCEEDED) {
            log.info(
                    "Wallet 보상 완료 후 늦은 동결 성공 수신. "
                            + "subscriptionId={}, eventId={}, holdId={}",
                    subscriptionId,
                    envelope.eventId(),
                    envelope.payload().holdId()
            );
            return;
        }

        /*
         * 공모 중단·모집 미달 보상 도중 늦게 HOLD 성공이 확인된 경우,
         * 보상 요청을 다시 저장한다.
         *
         * 기존 요청이 HOLD보다 먼저 처리되어 HOLD_NOT_FOUND가
         * 발생했을 가능성도 있으므로, Wallet이 실제 상태를 재판단한다.
         * 보상 결과를 받기 전까지 기존 보상 상태는 유지한다.
         */
        if (subscription.getSubscriptionStatus()
                == SubscriptionStatus.COMPENSATING
                && subscription.getCancellationType() != null
                && compensation != null) {

            Offering offering = offeringRepository
                    .findById(subscription.getOfferingId())
                    .orElseThrow(() -> new BusinessException(
                            OfferingErrorCode.OFFERING_NOT_FOUND
                    ));

            subscriptionEventPublisher.publishCompensationRequested(
                    subscription,
                    offering.getAssetId(),
                    envelope.correlationId()
            );

            log.warn(
                    "보상 중 늦은 동결 성공 수신으로 보상 재요청 저장. "
                            + "subscriptionId={}, eventId={}, holdId={}",
                    subscriptionId,
                    envelope.eventId(),
                    envelope.payload().holdId()
            );
            return;
        }

        /*
         * 타임아웃, 보상 진행 정보 누락, 종료 상태와 충돌하는 결과 등은
         * 자동 확정하거나 성공으로 간주하지 않고 수동 확인 대상으로 남긴다.
         */
        SubscriptionStatus previousStatus =
                subscription.getSubscriptionStatus();

        subscription.requireManualReview(
                "LATE_WALLET_HOLD_SUCCEEDED"
        );

        log.error(
                "늦은 동결 성공으로 수동 확인 필요. "
                        + "subscriptionId={}, eventId={}, holdId={}, "
                        + "previousStatus={}",
                subscriptionId,
                envelope.eventId(),
                envelope.payload().holdId(),
                previousStatus
        );
    }

    private UUID parseSubscriptionId(String aggregateId) {
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
}