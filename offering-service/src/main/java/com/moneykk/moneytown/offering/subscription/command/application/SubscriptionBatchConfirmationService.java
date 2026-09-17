package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import com.moneykk.moneytown.offering.subscription.monitoring.SubscriptionBatchConfirmationMetrics;
import com.moneykk.moneytown.offering.subscription.monitoring.SubscriptionLifecycleMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionBatchConfirmationService {

    private static final int DEFAULT_CONFIRMATION_BATCH_SIZE = 100;

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionEventPublisher subscriptionEventPublisher;
    private final SubscriptionLifecycleMetrics subscriptionLifecycleMetrics;
    private final SubscriptionBatchConfirmationMetrics subscriptionBatchConfirmationMetrics;

    @Value("${subscription.confirmation.batch-size:100}")
    private int confirmationBatchSize = DEFAULT_CONFIRMATION_BATCH_SIZE;

    /**
     * 최종 확정 조건을 만족한 공모의 청약을 제한된 크기로 확정한다.
     *
     * 호출 서비스는 반드시 공모를 먼저 잠근 상태여야 한다.
     * 공모 → 청약 순서로 잠금을 획득하여 관리자 공모 중단,
     * 다른 확정 배치와의 동시 실행을 직렬화한다.
     *
     * SOLD_OUT 또는 CLOSED 상태이고 잔여 수량이 0이며,
     * 수량을 확보한 모든 청약의 Wallet HOLD 처리가 끝난 경우에만
     * HOLD_SUCCEEDED 청약을 최대 confirmationBatchSize건 확정한다.
     *
     * 이미 CONFIRMED인 청약은 조회하지 않으므로 재실행해도
     * 같은 청약에 대한 확정 이벤트를 중복 생성하지 않는다.
     *
     * @param offering 잠금이 획득된 공모
     * @param correlationId 원본 이벤트 또는 재처리 요청의 추적 ID
     * @return 이번 배치에서 CONFIRMED로 전환한 청약 수
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int confirmNextBatchIfReady(
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
         * 아직 청약 확정 대상이 아니다.
         */
        if (!finalizableOffering) {
            log.debug(
                    "공모 확정 조건 미충족. "
                            + "offeringId={}, status={}, remainingQuantity={}",
                    offering.getOfferingId(),
                    offering.getOfferingStatus(),
                    offering.getRemainingQuantity()
            );

            return 0;
        }

        long batchStartedNanos = System.nanoTime();

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
         * 아직 확정되지 않은 HOLD_SUCCEEDED 청약 중
         * subscriptionId 순서로 최대 confirmationBatchSize건만 잠근다.
         *
         * 이미 CONFIRMED인 청약은 조회하지 않으므로
         * 재실행해도 같은 청약의 확정 이벤트를 중복 생성하지 않는다.
         */
        List<Subscription> confirmationBatch =
                subscriptionRepository
                        .findHoldSucceededBatchForUpdate(
                                offering.getOfferingId(),
                                PageRequest.of(
                                        0,
                                        confirmationBatchSize
                                )
                        );

        /*
         * 처리할 HOLD_SUCCEEDED 청약이 없다면
         * 모든 대상이 이미 확정됐거나 확정 대상이 없는 상태다.
         */
        if (confirmationBatch.isEmpty()) {
            log.debug(
                    "확정할 청약 배치가 없음. offeringId={}",
                    offering.getOfferingId()
            );

            return 0;
        }

        Instant confirmedAt = Instant.now();

        for (Subscription subscription : confirmationBatch) {
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
        }

        int confirmedCount = confirmationBatch.size();

        log.info(
                "청약 확정 배치 처리 완료. "
                        + "offeringId={}, confirmedCount={}, batchSize={}",
                offering.getOfferingId(),
                confirmedCount,
                confirmationBatchSize
        );

        subscriptionBatchConfirmationMetrics.publish(
                Duration.ofNanos(
                        Math.max(
                                0L,
                                System.nanoTime() - batchStartedNanos
                        )
                ),
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
