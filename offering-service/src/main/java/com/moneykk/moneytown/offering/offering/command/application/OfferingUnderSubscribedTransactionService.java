package com.moneykk.moneytown.offering.offering.command.application;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.CancellationType;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionCompensation;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionCompensationRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OfferingUnderSubscribedTransactionService {

    private static final List<SubscriptionStatus>
            COMPENSATABLE_STATUSES = List.of(
            SubscriptionStatus.PROCESSING,
            SubscriptionStatus.HOLD_SUCCEEDED,
            SubscriptionStatus.CONFIRMED
    );

    private final OfferingRepository offeringRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionCompensationRepository subscriptionCompensationRepository;
    private final SubscriptionEventPublisher subscriptionEventPublisher;
    private final OfferingCompensationCompletionService offeringCompensationCompletionService;

    /**
     * 모집이 종료됐지만 모집 수량을 채우지 못한 공모 한 건의
     * 취소 및 청약 보상 처리를 시작한다.
     *
     * 공모별 독립 트랜잭션으로 처리하여 특정 공모 처리 실패가
     * 다른 공모의 처리 결과를 롤백하지 않도록 한다.
     *
     * @param offeringId 처리할 공모 ID
     * @param now 스케줄러가 모집 미달 대상을 조회한 기준 시각
     * @return 실제로 모집 미달 취소를 시작했으면 true,
     *         이미 다른 작업에서 처리됐다면 false
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean startUnderSubscribedCancellation(
            UUID offeringId,
            Instant now
    ) {
        /*
         * 공모 한 건을 비관적 잠금으로 조회한다.
         *
         * 관리자 공모 중단, Wallet 결과 처리 등과
         * 공모 → 청약 순서로 잠금 순서를 통일한다.
         */
        Offering offering = offeringRepository
                .findByIdForUpdate(offeringId)
                .orElse(null);

        /*
         * 대상 ID 조회 후 공모가 삭제됐거나
         * 다른 작업에서 이미 처리했다면 건너뛴다.
         */
        if (offering == null) {
            return false;
        }

        /*
         * ID 목록을 조회한 시점과 실제 잠금을 획득한 시점 사이에
         * 공모 상태가 변경됐을 수 있으므로 조건을 다시 검증한다.
         */
        if (!isStillUnderSubscribed(offering, now)) {
            return false;
        }

        /*
         * OPEN, SOLD_OUT 또는 CLOSED 상태의 모집 미달 공모를
         * CANCELLING 상태로 전환한다.
         */
        offering.startUnderSubscribedCancellation();

        /*
         * 하나의 공모에서 발생하는 모든 보상 이벤트가
         * 같은 추적 ID를 사용하도록 공모별 correlationId를 만든다.
         */
        String correlationId = UUID.randomUUID().toString();

        /*
         * 공모 잠금 이후 보상 대상 청약을 잠금 조회한다.
         *
         * Wallet HOLD 결과를 기다리는 PROCESSING,
         * Wallet HOLD가 성공한 HOLD_SUCCEEDED,
         * 최종 확정된 CONFIRMED 청약을 모두 포함한다.
         */
        List<Subscription> subscriptions =
                subscriptionRepository
                        .findAllByOfferingIdAndSubscriptionStatusInAndIsDeletedFalse(
                                offeringId,
                                COMPENSATABLE_STATUSES
                        );

        for (Subscription subscription : subscriptions) {
            /*
             * PROCESSING/HOLD_SUCCEEDED/CONFIRMED
             * → COMPENSATING
             */
            subscription.startCompensation(
                    CancellationType.OFFERING_UNDER_SUBSCRIBED
            );

            /*
             * Wallet과 Holding의 보상 처리 상태를 추적할
             * 보상 엔티티를 생성한다.
             */
            SubscriptionCompensation compensation =
                    SubscriptionCompensation.create(
                            subscription.getSubscriptionId()
                    );

            subscriptionCompensationRepository.save(compensation);

            /*
             * 청약 상태 변경 및 보상 정보 저장과 같은 트랜잭션에서
             * 보상 요청 이벤트를 Outbox에 저장한다.
             */
            subscriptionEventPublisher.publishCompensationRequested(
                    subscription,
                    offering.getAssetId(),
                    correlationId
            );
        }

        /*
         * 보상 대상 청약이 없거나 모든 청약이 이미 해결된 경우
         * 공모 취소 완료 조건을 바로 확인한다.
         */
        offeringCompensationCompletionService.completeIfReady(
                offeringId
        );

        return true;
    }

    /**
     * 잠금을 획득한 시점에도 모집 미달 처리 대상인지 확인한다.
     */
    private boolean isStillUnderSubscribed(
            Offering offering,
            Instant now
    ) {
        OfferingStatus status = offering.getOfferingStatus();

        boolean cancellableStatus =
                status == OfferingStatus.OPEN
                        || status == OfferingStatus.SOLD_OUT
                        || status == OfferingStatus.CLOSED;

        return cancellableStatus
                && offering.getEndAt() != null
                && !offering.getEndAt().isAfter(now)
                && offering.getRemainingQuantity() != null
                && offering.getRemainingQuantity() > 0;
    }
}