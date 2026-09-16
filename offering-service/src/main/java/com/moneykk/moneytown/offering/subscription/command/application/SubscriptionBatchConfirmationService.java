package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import com.moneykk.moneytown.offering.subscription.monitoring.SubscriptionLifecycleMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionBatchConfirmationService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionEventPublisher subscriptionEventPublisher;
    private final SubscriptionLifecycleMetrics subscriptionLifecycleMetrics;

    /**
     * 매진된 공모에서 수량을 확보한 모든 청약의
     * Wallet HOLD가 성공했는지 확인하고 일괄 확정한다.
     *
     * 호출하는 서비스는 반드시 공모를 먼저 잠근 상태여야 한다.
     * 공모 → 청약 순서로 잠금을 획득하여 동시 처리 시
     * 교착 가능성을 줄인다.
     *
     * SOLD_OUT 또는 CLOSED 상태이고 잔여 수량이 0인 공모만
     * 청약 일괄 확정 대상이다.
     *
     * 모든 수량 확보 청약이 HOLD_SUCCEEDED 또는 CONFIRMED이면
     * 아직 HOLD_SUCCEEDED인 청약을 CONFIRMED로 전환하고
     * SubscriptionConfirmed 이벤트를 Outbox에 저장한다.
     *
     * @param offering 잠금이 획득된 공모
     * @param correlationId 원본 이벤트 또는 재처리 요청의 추적 ID
     * @return 이번 호출에서 새로 CONFIRMED로 전환한 청약 수
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int confirmAllIfReady(
            Offering offering,
            String correlationId
    ) {
        validateInput(
                offering,
                correlationId
        );

        boolean finalizableOffering =
                (
                        offering.getOfferingStatus()
                                == OfferingStatus.SOLD_OUT
                                || offering.getOfferingStatus()
                                == OfferingStatus.CLOSED
                )
                        && offering.getRemainingQuantity() == 0L;

        /*
         * 모집 중이거나 잔여 수량이 남은 공모는
         * 아직 전체 청약 확정 대상이 아니다.
         */
        if (!finalizableOffering) {
            log.debug(
                    "공모 전체 확정 조건 미충족. "
                            + "offeringId={}, status={}, remainingQuantity={}",
                    offering.getOfferingId(),
                    offering.getOfferingStatus(),
                    offering.getRemainingQuantity()
            );

            return 0;
        }

        /*
         * 공모 잠금이 이미 획득된 상태에서 미완료 청약의 존재 여부만 먼저 확인한다.
         *
         * PROCESSING 등 아직 Wallet HOLD 결과를 기다리는 청약이 있으면
         * 전체 청약 엔티티를 조회하거나 행 잠금을 획득하지 않는다.
         */
        boolean hasPendingHold = subscriptionRepository
                .existsReservedSubscriptionAwaitingHold(
                        offering.getOfferingId()
                );

        if (hasPendingHold) {
            log.debug(
                    "일부 청약의 Wallet HOLD 결과 대기 중. offeringId={}",
                    offering.getOfferingId()
            );

            return 0;
        }

        /*
         * 현재 수량을 확보하고 있는 모든 청약을 잠근다.
         *
         * Hold 실패 후 수량이 복원된 REJECTED 청약은
         * quantityReserved=false이므로 제외된다.
         */
        List<Subscription> reservedSubscriptions =
                subscriptionRepository
                        .findAllReservedByOfferingIdForUpdate(
                                offering.getOfferingId()
                        );

        /*
         * 잔여 수량이 0인데 수량 확보 청약이 없다면
         * 공모와 청약 데이터가 일치하지 않는 상태다.
         */
        if (reservedSubscriptions.isEmpty()) {
            throw new IllegalStateException(
                    "매진된 공모에 수량 확보 청약이 존재하지 않습니다. "
                            + "offeringId="
                            + offering.getOfferingId()
            );
        }

        /*
         * 기존 데이터와 재처리를 고려하여 CONFIRMED도
         * Wallet HOLD가 완료된 상태로 인정한다.
         */
        boolean allHoldsSucceeded =
                reservedSubscriptions.stream()
                        .allMatch(subscription ->
                                subscription.getSubscriptionStatus()
                                        == SubscriptionStatus.HOLD_SUCCEEDED
                                        || subscription.getSubscriptionStatus()
                                        == SubscriptionStatus.CONFIRMED
                        );

        /*
         * PROCESSING 등 Wallet HOLD 결과를 기다리는 청약이
         * 하나라도 있으면 일괄 확정을 시작하지 않는다.
         */
        if (!allHoldsSucceeded) {
            log.debug(
                    "일부 청약의 Wallet HOLD 결과 대기 중. "
                            + "offeringId={}, reservedSubscriptionCount={}",
                    offering.getOfferingId(),
                    reservedSubscriptions.size()
            );

            return 0;
        }

        Instant confirmedAt = Instant.now();
        int confirmedCount = 0;

        /*
         * 모든 HOLD가 성공한 경우 아직 HOLD_SUCCEEDED인
         * 청약만 CONFIRMED로 전환한다.
         *
         * 이미 CONFIRMED인 청약은 중복 이벤트를 발행하지 않는다.
         */
        for (Subscription subscription : reservedSubscriptions) {
            if (subscription.getSubscriptionStatus()
                    == SubscriptionStatus.CONFIRMED) {
                continue;
            }

            subscription.confirm(confirmedAt);

            subscriptionEventPublisher.publishConfirmed(
                    subscription,
                    offering.getAssetId(),
                    correlationId
            );

            subscriptionLifecycleMetrics.publishOutcome(
                    subscription,
                    SubscriptionLifecycleMetrics.Result.CONFIRMED,
                    confirmedAt
            );

            confirmedCount++;
        }

        log.info(
                "공모 전체 Wallet HOLD 성공으로 청약 일괄 확정. "
                        + "offeringId={}, confirmedCount={}",
                offering.getOfferingId(),
                confirmedCount
        );

        return confirmedCount;
    }

    private void validateInput(
            Offering offering,
            String correlationId
    ) {
        Objects.requireNonNull(
                offering,
                "offering은 필수입니다."
        );

        Objects.requireNonNull(
                offering.getOfferingId(),
                "offeringId는 필수입니다."
        );

        if (correlationId == null
                || correlationId.isBlank()) {
            throw new IllegalArgumentException(
                    "correlationId는 필수입니다."
            );
        }
    }
}
