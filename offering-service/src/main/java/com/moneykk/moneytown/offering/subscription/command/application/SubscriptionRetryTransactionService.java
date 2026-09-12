package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionRetryResponse;
import com.moneykk.moneytown.offering.subscription.domain.entity.HoldingAllocationStatus;
import com.moneykk.moneytown.offering.subscription.domain.entity.IdempotencyOperation;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.IdempotencyRequestRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.HoldingSubscriptionStatusResponse;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.WalletHoldStatus;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.WalletHoldStatusResponse;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 관리자 청약 재처리 요청의 상태 변경과
 * Outbox 이벤트 저장을 하나의 로컬 트랜잭션으로 처리한다.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionRetryTransactionService {

    private final OfferingRepository offeringRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final IdempotencyRequestRepository idempotencyRequestRepository;
    private final SubscriptionEventPublisher subscriptionEventPublisher;

    @Value("${subscription.reservation-timeout-minutes:10}")
    private long reservationTimeoutMinutes;

    /**
     * 외부 서비스의 실제 상태를 기준으로
     * 청약의 누락된 처리 단계만 다시 시작한다.
     *
     * - Wallet Hold 미존재: PROCESSING 복구 후 SubscriptionReserved 발행
     * - Wallet HELD: HOLD_SUCCEEDED 복구 후 전체 확정 조건 재확인
     * - Wallet COMMITTED: CONFIRMED 복구 후 필요한 Holding 배정만 재요청
     */
    @Transactional
    public SubscriptionRetryResponse retry(
            UUID subscriptionId,
            UUID adminId,
            String idempotencyKey,
            String correlationId,
            WalletHoldStatusResponse walletStatus,
            HoldingSubscriptionStatusResponse holdingStatus
    ) {
        UUID offeringId = subscriptionRepository
                .findOfferingIdBySubscriptionId(subscriptionId)
                .orElseThrow(() -> new BusinessException(
                        SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND
                ));

        /*
         * 기존 Wallet 결과 처리 및 보상 처리와 잠금 순서를 통일한다.
         *
         * Offering → Subscription → 같은 공모의 전체 Subscription
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

        validateHoldingResponse(
                subscription,
                offering,
                holdingStatus
        );

        /*
         * CONFIRMED이면서 Holding 배정에 실패한 청약도
         * 정상 진행 방향의 재처리를 허용한다.
         *
         * Holding 배정 실패는 청약 상태를 MANUAL_REVIEW로 변경하지 않고
         * CONFIRMED + FAILED로 유지하기 때문이다.
         */
        if (isConfirmedHoldingRetry(subscription)) {
            retryConfirmedHolding(
                    subscription,
                    offering,
                    walletStatus,
                    holdingStatus,
                    correlationId
            );
        } else {
            retryManualReview(
                    subscription,
                    offering,
                    walletStatus,
                    holdingStatus,
                    correlationId
            );
        }

        completeIdempotencyRequest(
                subscriptionId,
                adminId,
                idempotencyKey
        );

        return SubscriptionRetryResponse.from(subscription);
    }

    /**
     * MANUAL_REVIEW 청약을 Wallet의 실제 상태에 맞춰 복구한다.
     */
    private void retryManualReview(
            Subscription subscription,
            Offering offering,
            WalletHoldStatusResponse walletStatus,
            HoldingSubscriptionStatusResponse holdingStatus,
            String correlationId
    ) {
        if (subscription.getSubscriptionStatus()
                != SubscriptionStatus.MANUAL_REVIEW) {
            throw new BusinessException(
                    SubscriptionErrorCode.SUBSCRIPTION_RETRY_NOT_ALLOWED
            );
        }

        /*
         * Wallet Hold가 존재하지 않는 경우다.
         *
         * 새로운 예약 만료 시각을 부여하고
         * Wallet Hold 요청 이벤트를 다시 발행한다.
         */
        if (walletStatus == null) {
            retryWalletHold(
                    subscription,
                    offering,
                    holdingStatus,
                    correlationId
            );
            return;
        }

        validateWalletResponse(
                subscription,
                walletStatus
        );

        switch (walletStatus.status()) {
            case HELD -> recoverHeldSubscription(
                    subscription,
                    offering,
                    holdingStatus,
                    correlationId
            );

            case COMMITTED -> recoverCommittedSubscription(
                    subscription,
                    offering,
                    holdingStatus,
                    correlationId
            );

            /*
             * RELEASED 또는 REFUNDED는 이미 보상 방향으로 처리된 상태다.
             * 정상 청약 방향으로 되돌리지 않는다.
             */
            case RELEASED, REFUNDED ->
                    throw new BusinessException(
                            SubscriptionErrorCode
                                    .SUBSCRIPTION_RETRY_NOT_ALLOWED
                    );
        }
    }

    /**
     * Wallet Hold가 아직 생성되지 않은 청약을 PROCESSING으로 복구하고
     * SubscriptionReserved 이벤트를 다시 저장한다.
     */
    private void retryWalletHold(
            Subscription subscription,
            Offering offering,
            HoldingSubscriptionStatusResponse holdingStatus,
            String correlationId
    ) {
        /*
         * 이미 공모가 중단 또는 취소 중이면
         * 정상 청약 방향으로 재처리할 수 없다.
         */
        boolean retryableOffering =
                offering.getOfferingStatus() == OfferingStatus.OPEN
                        || offering.getOfferingStatus()
                        == OfferingStatus.SOLD_OUT
                        || offering.getOfferingStatus()
                        == OfferingStatus.CLOSED;

        if (!retryableOffering) {
            throw new BusinessException(
                    SubscriptionErrorCode.SUBSCRIPTION_RETRY_NOT_ALLOWED
            );
        }

        /*
         * Wallet Hold가 없는데 Holding 처리 이력이 존재한다면
         * 서비스 간 상태가 서로 일치하지 않는다.
         */
        if (holdingStatus.allocationProcessed()
                || holdingStatus.revocationProcessed()
                || holdingStatus.allocationBlocked()) {
            throw new BusinessException(
                    SubscriptionErrorCode.EXTERNAL_RESPONSE_INVALID
            );
        }

        Instant newReservationExpiresAt =
                Instant.now().plus(
                        reservationTimeoutMinutes,
                        ChronoUnit.MINUTES
                );

        subscription.restartProcessing(
                newReservationExpiresAt
        );

        subscriptionEventPublisher.publishReserved(
                subscription,
                correlationId
        );
    }

    /**
     * Wallet에는 HELD가 존재하지만
     * Offering에 성공 결과가 반영되지 않은 청약을 복구한다.
     */
    private void recoverHeldSubscription(
            Subscription subscription,
            Offering offering,
            HoldingSubscriptionStatusResponse holdingStatus,
            String correlationId
    ) {
        /*
         * Wallet이 아직 HELD인데 Holding 배정 또는 회수 이력이 있으면
         * 정상 상태 흐름과 일치하지 않는다.
         */
        if (holdingStatus.allocationProcessed()
                || holdingStatus.revocationProcessed()
                || holdingStatus.allocationBlocked()) {
            throw new BusinessException(
                    SubscriptionErrorCode.EXTERNAL_RESPONSE_INVALID
            );
        }

        subscription.restartHoldSucceeded();

        /*
         * 매진 공모의 모든 Wallet HOLD가 성공했다면
         * 기존 WalletHoldSucceeded 처리와 동일하게 일괄 확정한다.
         */
        confirmAllIfReady(
                offering,
                correlationId
        );
    }

    /**
     * Wallet COMMIT까지 완료됐지만
     * Offering 또는 Holding의 후속 반영이 누락된 청약을 복구한다.
     */
    private void recoverCommittedSubscription(
            Subscription subscription,
            Offering offering,
            HoldingSubscriptionStatusResponse holdingStatus,
            String correlationId
    ) {
        if (holdingStatus.revocationProcessed()
                || holdingStatus.allocationBlocked()) {
            throw new BusinessException(
                    SubscriptionErrorCode.SUBSCRIPTION_RETRY_NOT_ALLOWED
            );
        }

        subscription.restartConfirmed();

        if (holdingStatus.allocationProcessed()) {
            /*
             * Holding 배정도 이미 완료됐다면
             * 이벤트를 다시 발행하지 않고 로컬 상태만 성공으로 맞춘다.
             */
            subscription.markHoldingAllocationSucceeded();
            return;
        }

        /*
         * Wallet COMMIT은 완료됐지만 Holding 배정이 없다면
         * SubscriptionConfirmed 이벤트를 다시 발행한다.
         */
        subscription.prepareHoldingAllocationRetry();

        subscriptionEventPublisher.publishConfirmed(
                subscription,
                offering.getAssetId(),
                correlationId
        );
    }

    /**
     * CONFIRMED + Holding FAILED 상태의 청약을 재처리한다.
     */
    private void retryConfirmedHolding(
            Subscription subscription,
            Offering offering,
            WalletHoldStatusResponse walletStatus,
            HoldingSubscriptionStatusResponse holdingStatus,
            String correlationId
    ) {
        /*
         * 청약이 확정됐다면 Wallet도 COMMITTED 상태여야 한다.
         */
        if (walletStatus == null) {
            throw new BusinessException(
                    SubscriptionErrorCode.EXTERNAL_RESPONSE_INVALID
            );
        }

        validateWalletResponse(
                subscription,
                walletStatus
        );

        if (walletStatus.status()
                != WalletHoldStatus.COMMITTED) {
            throw new BusinessException(
                    SubscriptionErrorCode.SUBSCRIPTION_RETRY_NOT_ALLOWED
            );
        }

        if (holdingStatus.revocationProcessed()
                || holdingStatus.allocationBlocked()) {
            throw new BusinessException(
                    SubscriptionErrorCode.SUBSCRIPTION_RETRY_NOT_ALLOWED
            );
        }

        if (holdingStatus.allocationProcessed()) {
            /*
             * 이벤트 결과만 유실됐고 Holding 배정은 완료된 경우다.
             */
            subscription.markHoldingAllocationSucceeded();
            return;
        }

        /*
         * Holding 배정 자체가 완료되지 않았다면
         * 로컬 상태를 PENDING으로 변경하고 확정 이벤트를 재발행한다.
         */
        subscription.prepareHoldingAllocationRetry();

        subscriptionEventPublisher.publishConfirmed(
                subscription,
                offering.getAssetId(),
                correlationId
        );
    }

    /**
     * 매진된 공모에서 수량을 확보한 모든 청약의
     * Wallet HOLD가 성공했는지 확인하고 일괄 확정한다.
     */
    private void confirmAllIfReady(
            Offering offering,
            String correlationId
    ) {
        boolean finalizableOffering =
                (
                        offering.getOfferingStatus()
                                == OfferingStatus.SOLD_OUT
                                || offering.getOfferingStatus()
                                == OfferingStatus.CLOSED
                )
                        && offering.getRemainingQuantity() == 0L;

        if (!finalizableOffering) {
            return;
        }

        List<Subscription> reservedSubscriptions =
                subscriptionRepository
                        .findAllReservedByOfferingIdForUpdate(
                                offering.getOfferingId()
                        );

        if (reservedSubscriptions.isEmpty()) {
            throw new IllegalStateException(
                    "매진된 공모에 수량 확보 청약이 존재하지 않습니다. "
                            + "offeringId="
                            + offering.getOfferingId()
            );
        }

        boolean allHoldsSucceeded =
                reservedSubscriptions.stream()
                        .allMatch(candidate ->
                                candidate.getSubscriptionStatus()
                                        == SubscriptionStatus.HOLD_SUCCEEDED
                                        || candidate.getSubscriptionStatus()
                                        == SubscriptionStatus.CONFIRMED
                        );

        if (!allHoldsSucceeded) {
            return;
        }

        Instant confirmedAt = Instant.now();

        for (Subscription candidate : reservedSubscriptions) {
            if (candidate.getSubscriptionStatus()
                    == SubscriptionStatus.CONFIRMED) {
                continue;
            }

            candidate.confirm(confirmedAt);

            subscriptionEventPublisher.publishConfirmed(
                    candidate,
                    offering.getAssetId(),
                    correlationId
            );
        }
    }

    /**
     * Wallet 응답이 현재 청약과 일치하는지 검증한다.
     */
    private void validateWalletResponse(
            Subscription subscription,
            WalletHoldStatusResponse walletStatus
    ) {
        if (!subscription.getSubscriptionId().equals(
                walletStatus.subscriptionId()
        )
                || walletStatus.amount() == null
                || !Objects.equals(
                walletStatus.amount(),
                subscription.getAmount()
        )
                || walletStatus.status() == null
                || walletStatus.updatedAt() == null) {
            throw new BusinessException(
                    SubscriptionErrorCode.EXTERNAL_RESPONSE_INVALID
            );
        }
    }

    /**
     * Holding 응답이 현재 청약과 일치하는지 검증한다.
     */
    private void validateHoldingResponse(
            Subscription subscription,
            Offering offering,
            HoldingSubscriptionStatusResponse holdingStatus
    ) {
        if (holdingStatus == null
                || !subscription.getSubscriptionId().equals(
                holdingStatus.subscriptionId()
        )) {
            throw new BusinessException(
                    SubscriptionErrorCode.EXTERNAL_RESPONSE_INVALID
            );
        }

        if (holdingStatus.lastProcessedAt() == null
                && (
                holdingStatus.allocationProcessed()
                        || holdingStatus.revocationProcessed()
                        || holdingStatus.allocationBlocked()
        )) {
            throw new BusinessException(
                    SubscriptionErrorCode.EXTERNAL_RESPONSE_INVALID
            );
        }

        /*
         * 배정 이력이 있는 경우에만
         * 자산·투자자·수량 정보를 검증한다.
         */
        if (holdingStatus.allocationProcessed()
                && (
                holdingStatus.holdingId() == null
                        || !Objects.equals(
                        holdingStatus.assetId(),
                        offering.getAssetId()
                )
                        || !Objects.equals(
                        holdingStatus.userId(),
                        subscription.getUserId()
                )
                        || holdingStatus.allocatedQuantity()
                        != subscription.getQuantity()
        )) {
            throw new BusinessException(
                    SubscriptionErrorCode.EXTERNAL_RESPONSE_INVALID
            );
        }
    }

    private boolean isConfirmedHoldingRetry(
            Subscription subscription
    ) {
        return subscription.getSubscriptionStatus()
                == SubscriptionStatus.CONFIRMED
                && subscription.getHoldingAllocationStatus()
                == HoldingAllocationStatus.FAILED;
    }

    /**
     * 상태 변경과 Outbox 저장이 완료된 뒤
     * 멱등 요청을 COMPLETED로 전환한다.
     */
    private void completeIdempotencyRequest(
            UUID subscriptionId,
            UUID adminId,
            String idempotencyKey
    ) {
        int completed = idempotencyRequestRepository.complete(
                adminId,
                IdempotencyOperation.RETRY_SUBSCRIPTION.name(),
                idempotencyKey,
                subscriptionId,
                HttpStatus.ACCEPTED.value()
        );

        if (completed != 1) {
            throw new BusinessException(
                    SubscriptionErrorCode.IDEMPOTENCY_COMPLETION_FAILED
            );
        }
    }
}