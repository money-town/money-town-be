package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionCompensation;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionCompensationRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionTimeoutTransactionService {

    private final OfferingRepository offeringRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionCompensationRepository
            subscriptionCompensationRepository;
    private final SubscriptionEventPublisher subscriptionEventPublisher;

    /**
     * 예약 시간이 만료된 청약 한 건의 보상 처리를 시작한다.
     *
     * 각 청약을 독립된 트랜잭션으로 처리하여
     * 특정 청약에서 오류가 발생해도 이미 처리된 다른 청약이
     * 함께 롤백되지 않도록 한다.
     *
     * @param subscriptionId 처리할 청약 ID
     * @param now 스케줄러가 만료 대상을 조회한 기준 시각
     * @return 실제로 만료 보상을 시작했으면 true,
     *         이미 다른 작업에서 처리됐다면 false
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processExpiredReservation(
            UUID subscriptionId,
            Instant now
    ) {
        /*
         * 청약을 잠그기 전에 offeringId만 먼저 조회한다.
         *
         * Offering → Subscription 순서로 잠금을 획득하여
         * Wallet 결과 처리, 공모 취소 처리와 잠금 순서를 맞춘다.
         */
        UUID offeringId = subscriptionRepository
                .findOfferingIdBySubscriptionId(subscriptionId)
                .orElseThrow(
                        () -> new BusinessException(
                                SubscriptionErrorCode
                                        .SUBSCRIPTION_NOT_FOUND
                        )
                );

        /*
         * 공모를 먼저 비관적 잠금으로 조회한다.
         *
         * 동일 공모에서 Wallet HOLD 결과나 공모 취소가
         * 동시에 처리되는 상황을 순차적으로 처리한다.
         */
        Offering offering = offeringRepository
                .findByIdForUpdate(offeringId)
                .orElseThrow(
                        () -> new BusinessException(
                                OfferingErrorCode.OFFERING_NOT_FOUND
                        )
                );

        /*
         * 공모 잠금 이후 청약 행을 비관적 잠금으로 조회한다.
         */
        Subscription subscription = subscriptionRepository
                .findByIdForUpdate(subscriptionId)
                .orElseThrow(
                        () -> new BusinessException(
                                SubscriptionErrorCode
                                        .SUBSCRIPTION_NOT_FOUND
                        )
                );

        /*
         * 만료 대상 ID 조회 이후 Wallet 결과 처리 등으로
         * 상태가 변경됐을 수 있으므로 잠금 획득 후 다시 검증한다.
         *
         * 이미 처리된 청약은 오류로 만들지 않고 건너뛴다.
         */
        if (!subscription.isReservationExpired(now)) {
            return false;
        }

        /*
         * PROCESSING → COMPENSATING
         * failureCode에는 RESERVATION_EXPIRED가 기록된다.
         */
        subscription.startExpirationCompensation(now);

        /*
         * 예약 만료 보상 진행 정보를 생성한다.
         *
         * 예약 만료는 공모 취소 보상과 달리
         * Holding 처리가 필요하지 않은 보상 유형으로 생성된다.
         */
        SubscriptionCompensation compensation =
                SubscriptionCompensation
                        .createForReservationExpiration(
                                subscription.getSubscriptionId()
                        );

        subscriptionCompensationRepository.save(compensation);

        /*
         * 상태 변경 및 보상 정보 저장과 같은 트랜잭션에서
         * 보상 요청 이벤트를 Outbox에 저장한다.
         */
        String correlationId = UUID.randomUUID().toString();

        subscriptionEventPublisher.publishCompensationRequested(
                subscription,
                offering.getAssetId(),
                correlationId
        );

        return true;
    }
}