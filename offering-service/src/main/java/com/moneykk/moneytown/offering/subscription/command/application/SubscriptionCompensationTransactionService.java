package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionCompensationResponse;
import com.moneykk.moneytown.offering.subscription.domain.entity.IdempotencyOperation;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionCompensation;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.IdempotencyRequestRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionCompensationRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.HoldingSubscriptionStatusResponse;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.WalletHoldStatus;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.WalletHoldStatusResponse;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * 관리자 청약 보상 요청의 DB 변경과 Outbox 저장을
 * 하나의 로컬 트랜잭션으로 처리한다.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionCompensationTransactionService {

    private final OfferingRepository offeringRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionCompensationRepository
            subscriptionCompensationRepository;
    private final IdempotencyRequestRepository idempotencyRequestRepository;

    private final SubscriptionEventPublisher subscriptionEventPublisher;
    private final SubscriptionCompensationCompletionService
            subscriptionCompensationCompletionService;

    /**
     * MANUAL_REVIEW 청약의 보상을 다시 시작한다.
     */
    @Transactional
    public SubscriptionCompensationResponse compensate(
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
         * 기존 보상 결과 처리와 동일하게
         * Offering → Subscription → Compensation 순서로 잠근다.
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

        subscription.restartCompensation();

        /*
         * 현재 완료 서비스가 처리할 수 있는 보상 원인은
         * 공모 취소 또는 예약 만료 보상이다.
         */
        boolean reservationExpiration =
                subscription.isReservationExpirationCompensation();

        boolean offeringCancellation =
                subscription.getCancellationType() != null;

        if (!reservationExpiration && !offeringCancellation) {
            throw new BusinessException(
                    SubscriptionErrorCode
                            .SUBSCRIPTION_COMPENSATION_NOT_ALLOWED
            );
        }

        SubscriptionCompensation compensation =
                getOrCreateCompensation(
                        subscription,
                        reservationExpiration
                );

        boolean walletRetryRequired =
                reconcileWalletStatus(
                        subscription,
                        compensation,
                        walletStatus
                );

        boolean holdingRetryRequired =
                reconcileHoldingStatus(
                        offering,
                        subscription,
                        compensation,
                        holdingStatus
                );

        /*
         * Wallet 또는 Holding 중 하나라도 보상이 필요하면
         * 기존 보상 이벤트를 한 번만 다시 발행한다.
         *
         * Wallet과 Asset 소비자는 자신의 실제 상태를 기준으로
         * 필요한 처리만 수행한다.
         */
        if (walletRetryRequired || holdingRetryRequired) {
            subscriptionEventPublisher.publishCompensationRequested(
                    subscription,
                    offering.getAssetId(),
                    correlationId
            );
        } else {
            /*
             * 외부 서비스의 보상이 이미 모두 완료된 경우
             * 이벤트를 다시 발행하지 않고 로컬 완료 처리를 진행한다.
             */
            subscriptionCompensationCompletionService.completeIfReady(
                    subscriptionId
            );
        }

        int completed = idempotencyRequestRepository.complete(
                adminId,
                IdempotencyOperation.COMPENSATE_SUBSCRIPTION.name(),
                idempotencyKey,
                subscriptionId,
                HttpStatus.ACCEPTED.value()
        );

        if (completed != 1) {
            throw new BusinessException(
                    SubscriptionErrorCode.IDEMPOTENCY_COMPLETION_FAILED
            );
        }

        return SubscriptionCompensationResponse.from(subscription);
    }

    /**
     * 이미 보상이 완료된 청약을 새로운 멱등키로 호출한 경우
     * 추가 보상을 실행하지 않고 멱등 요청만 완료한다.
     */
    @Transactional
    public SubscriptionCompensationResponse completeExistingResult(
            UUID subscriptionId,
            UUID adminId,
            String idempotencyKey
    ) {
        Subscription subscription = subscriptionRepository
                .findByIdForUpdate(subscriptionId)
                .orElseThrow(() -> new BusinessException(
                        SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND
                ));

        if (subscription.getSubscriptionStatus()
                != SubscriptionStatus.REJECTED
                && subscription.getSubscriptionStatus()
                != SubscriptionStatus.CANCELLED) {
            throw new BusinessException(
                    SubscriptionErrorCode
                            .SUBSCRIPTION_COMPENSATION_NOT_ALLOWED
            );
        }

        int completed = idempotencyRequestRepository.complete(
                adminId,
                IdempotencyOperation.COMPENSATE_SUBSCRIPTION.name(),
                idempotencyKey,
                subscriptionId,
                HttpStatus.ACCEPTED.value()
        );

        if (completed != 1) {
            throw new BusinessException(
                    SubscriptionErrorCode.IDEMPOTENCY_COMPLETION_FAILED
            );
        }

        return SubscriptionCompensationResponse.from(subscription);
    }

    private SubscriptionCompensation getOrCreateCompensation(
            Subscription subscription,
            boolean reservationExpiration
    ) {
        return subscriptionCompensationRepository
                .findBySubscriptionIdForUpdate(
                        subscription.getSubscriptionId()
                )
                .orElseGet(() -> {
                    SubscriptionCompensation created =
                            reservationExpiration
                                    ? SubscriptionCompensation
                                    .createForReservationExpiration(
                                            subscription
                                                    .getSubscriptionId()
                                    )
                                    : SubscriptionCompensation.create(
                                    subscription.getSubscriptionId()
                            );

                    return subscriptionCompensationRepository.save(
                            created
                    );
                });
    }

    /**
     * Wallet 실제 상태를 Offering 보상 상태와 맞춘다.
     *
     * walletStatus가 null이면 Wallet Hold가 존재하지 않는 404 결과다.
     */
    private boolean reconcileWalletStatus(
            Subscription subscription,
            SubscriptionCompensation compensation,
            WalletHoldStatusResponse walletStatus
    ) {
        if (walletStatus == null) {
            compensation.markWalletSucceeded();
            return false;
        }

        if (!Objects.equals(
                walletStatus.amount(),
                subscription.getAmount()
        )) {
            throw new BusinessException(
                    SubscriptionErrorCode.EXTERNAL_RESPONSE_INVALID
            );
        }

        if (walletStatus.status() == WalletHoldStatus.RELEASED
                || walletStatus.status() == WalletHoldStatus.REFUNDED) {
            compensation.markWalletSucceeded();
            return false;
        }

        if (walletStatus.status() == WalletHoldStatus.HELD
                || walletStatus.status() == WalletHoldStatus.COMMITTED) {
            compensation.prepareWalletRetry();
            return true;
        }

        throw new BusinessException(
                SubscriptionErrorCode.EXTERNAL_RESPONSE_INVALID
        );
    }

    /**
     * Holding 실제 상태를 Offering 보상 상태와 맞춘다.
     */
    private boolean reconcileHoldingStatus(
            Offering offering,
            Subscription subscription,
            SubscriptionCompensation compensation,
            HoldingSubscriptionStatusResponse holdingStatus
    ) {
        if (holdingStatus.revocationProcessed()) {
            compensation.markHoldingSucceeded();
            return false;
        }

        if (!holdingStatus.allocationProcessed()) {
            compensation.markHoldingSucceeded();
            return false;
        }

        /*
         * 배정 이력이 있다면 청약, 자산, 투자자 및 수량이
         * 현재 보상 대상과 일치해야 한다.
         */
        if (holdingStatus.holdingId() == null
                || !Objects.equals(
                holdingStatus.assetId(),
                offering.getAssetId()
        )
                || !Objects.equals(
                holdingStatus.userId(),
                subscription.getUserId()
        )
                || holdingStatus.allocatedQuantity()
                != subscription.getQuantity()) {
            throw new BusinessException(
                    SubscriptionErrorCode.EXTERNAL_RESPONSE_INVALID
            );
        }

        compensation.prepareHoldingRetry();
        return true;
    }
}