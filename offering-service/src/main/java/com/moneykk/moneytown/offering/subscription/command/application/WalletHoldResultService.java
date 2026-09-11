package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.config.JpaAuditingConfig;
import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.global.processed.ProcessedEventService;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
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
import java.util.List;
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

    /**
     * Wallet HOLD 성공을 기록하고,
     * 공모 전체 확정 조건을 만족하면 유효한 모든 청약을 확정한다.
     *
     * 잠금 순서:
     * Offering → 현재 Subscription → 공모의 전체 유효 Subscription
     */
    private void confirmSubscription(
            UUID subscriptionId,
            EventEnvelope<WalletHoldSucceededPayload> envelope
    ) {
        /*
         *
         * 공모 중단·모집 미달 처리와 잠금 순서를 통일하기 위해
         * 청약을 잠그기 전에 offeringId만 먼저 조회한다.
         */
        UUID offeringId = subscriptionRepository
                .findOfferingIdBySubscriptionId(subscriptionId)
                .orElseThrow(() -> new BusinessException(
                        SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND
                ));

        /*
         *
         * 동일 공모의 다른 WalletHoldSucceeded 처리 및
         * 공모 취소 처리와 충돌하지 않도록 공모를 먼저 잠근다.
         */
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

        if (!subscription.getUserId().equals(envelope.userId())) {
            throw new IllegalArgumentException(
                    "동결 성공 이벤트의 userId가 청약자와 일치하지 않습니다."
            );
        }

        SubscriptionStatus currentStatus =
                subscription.getSubscriptionStatus();

        /*
         * 최초 성공 이벤트라면 PROCESSING → HOLD_SUCCEEDED로 변경한다.
         */
        if (currentStatus == SubscriptionStatus.PROCESSING) {
            subscription.markHoldSucceeded();

            /*
             * 서로 다른 eventId로 동일 성공 결과가 다시 수신될 수 있다.
             *
             * HOLD_SUCCEEDED는 이미 성공이 반영된 상태이므로 다시 변경하지 않고,
             * 다른 청약까지 모두 성공했는지 다시 확인한다.
             *
             * CONFIRMED도 이미 Wallet HOLD 성공을 거친 상태이므로
             * 전체 확정 조건을 다시 확인할 수 있다.
             */
        } else if (currentStatus == SubscriptionStatus.HOLD_SUCCEEDED
                || currentStatus == SubscriptionStatus.CONFIRMED) {

            log.info(
                    "이미 Wallet HOLD 성공이 반영된 청약. "
                            + "전체 확정 조건 재확인. "
                            + "subscriptionId={}, eventId={}, status={}",
                    subscriptionId,
                    envelope.eventId(),
                    currentStatus
            );

            /*
             * COMPENSATING, REJECTED, CANCELLED, MANUAL_REVIEW 상태에
             * 성공 이벤트가 늦게 도착한 경우 기존 예외 처리 흐름을 사용한다.
             */
        } else {
            handleLateHoldSucceeded(subscription, envelope);
            return;
        }

        /*
         *
         * 공모 수량이 전부 예약된 경우에만 전체 확정을 검토한다.
         *
         * CLOSED도 허용하는 이유:
         * 마지막 Wallet HOLD 결과가 늦게 도착하는 동안
         * 종료 스케줄러가 SOLD_OUT → CLOSED로 변경할 수 있기 때문이다.
         */
        boolean finalizableOffering =
                (offering.getOfferingStatus() == OfferingStatus.SOLD_OUT
                        || offering.getOfferingStatus() == OfferingStatus.CLOSED)
                        && offering.getRemainingQuantity() == 0L;

        if (!finalizableOffering) {
            log.debug(
                    "공모 전체 확정 조건 미충족. "
                            + "offeringId={}, status={}, remainingQuantity={}",
                    offeringId,
                    offering.getOfferingStatus(),
                    offering.getRemainingQuantity()
            );
            return;
        }

        /*
         *
         * 현재 수량을 확보하고 있는 모든 청약을 잠근다.
         * Hold 실패 후 수량이 복원된 REJECTED 청약은 제외된다.
         */
        List<Subscription> reservedSubscriptions =
                subscriptionRepository
                        .findAllReservedByOfferingIdForUpdate(offeringId);

        if (reservedSubscriptions.isEmpty()) {
            throw new IllegalStateException(
                    "매진된 공모에 수량 확보 청약이 존재하지 않습니다. "
                            + "offeringId=" + offeringId
            );
        }

        /*
         * 기존 데이터나 재처리를 고려해 CONFIRMED도
         * Wallet HOLD가 완료된 상태로 인정한다.
         */
        boolean allHoldsSucceeded = reservedSubscriptions.stream()
                .allMatch(candidate ->
                        candidate.getSubscriptionStatus()
                                == SubscriptionStatus.HOLD_SUCCEEDED
                                || candidate.getSubscriptionStatus()
                                == SubscriptionStatus.CONFIRMED
                );

        if (!allHoldsSucceeded) {
            log.debug(
                    "일부 청약의 Wallet HOLD 결과 대기 중. "
                            + "offeringId={}, reservedSubscriptionCount={}",
                    offeringId,
                    reservedSubscriptions.size()
            );
            return;
        }

        Instant confirmedAt = Instant.now();
        int confirmedCount = 0;

        /*
         * 모든 Hold가 성공한 경우 아직 HOLD_SUCCEEDED인 청약만
         * CONFIRMED로 전환하고 각각 확정 이벤트를 저장한다.
         *
         * 청약 상태 변경, Outbox 저장, 수신 이벤트 처리 이력은
         * ProcessedEventService의 동일 트랜잭션에서 커밋된다.
         */
        for (Subscription reservedSubscription : reservedSubscriptions) {
            if (reservedSubscription.getSubscriptionStatus()
                    == SubscriptionStatus.CONFIRMED) {
                continue;
            }

            reservedSubscription.confirm(confirmedAt);

            subscriptionEventPublisher.publishConfirmed(
                    reservedSubscription,
                    offering.getAssetId(),
                    envelope.correlationId()
            );

            confirmedCount++;
        }

        log.info(
                "공모 전체 Wallet HOLD 성공으로 청약 일괄 확정. "
                        + "offeringId={}, confirmedCount={}",
                offeringId,
                confirmedCount
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
        // PostFDS 이벤트의 assetId는 잠금 조회한 공모에서 가져온다.
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
        subscriptionEventPublisher.publishFailed(
                subscription,
                offering.getAssetId(),
                envelope.correlationId()
        );
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
         * 공모 취소 또는 예약 만료 보상 도중 늦게 HOLD 성공이 확인되면
         * 보상 요청을 다시 저장한다.
         *
         * 기존 요청이 HOLD보다 먼저 처리되어 HOLD_NOT_FOUND가
         * 발생했을 가능성도 있으므로 Wallet이 현재 금융 상태를
         * 다시 판단하도록 한다.
         *
         * 보상 결과를 받기 전까지 기존 보상 상태를 유지한다.
         */
        boolean compensationRequestAvailable =
                subscription.getCancellationType() != null
                        || subscription
                        .isReservationExpirationCompensation();

        if (subscription.getSubscriptionStatus()
                == SubscriptionStatus.COMPENSATING
                && compensationRequestAvailable
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
         * 보상 진행 정보 누락이나 종료 상태와 충돌하는 결과는
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