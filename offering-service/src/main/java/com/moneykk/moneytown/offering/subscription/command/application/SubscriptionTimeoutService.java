package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.offering.offering.command.scheduler.OfferingSchedulerMetrics;
import com.moneykk.moneytown.offering.subscription.command.config.SubscriptionTimeoutProperties;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.projection.ExpiredProcessingSubscriptionTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionTimeoutService {

    private final SubscriptionRepository subscriptionRepository;

    private final OfferingSchedulerMetrics offeringSchedulerMetrics;

    /*
     * 청약 한 건을 독립 트랜잭션으로 처리하는 서비스를 주입한다.
     */
    private final SubscriptionTimeoutTransactionService
            subscriptionTimeoutTransactionService;

    private final SubscriptionTimeoutProperties timeoutProperties;

    /**
     * 예약 유효시간이 만료된 PROCESSING 청약을
     * 설정된 크기의 키셋 배치로 조회하여 보상 처리를 시작한다.
     *
     * 한 번의 스케줄 실행에서 처리하는 최대 배치 수를 제한하여
     * 예약 만료 backlog가 많아도 스케줄러 스레드와 DB 커넥션을
     * 장시간 독점하지 않게 한다.
     *
     * 청약별 처리는 독립 트랜잭션에서 수행한다. 특정 청약이 실패해
     * PROCESSING 상태에 남더라도 같은 실행에서 후속 청약을 계속 처리한다.
     *
     * 처리하지 못한 청약과 실행 한도를 초과한 청약은
     * 다음 스케줄 실행에서 다시 조회된다.
     *
     * @return 실제로 만료 보상을 시작한 청약 수
     */
    public int processExpiredReservations() {

        Instant now = Instant.now();

        Instant lastReservationExpiresAt = null;
        UUID lastSubscriptionId = null;

        int processedCount = 0;

        for (int batchIndex = 0;
             batchIndex < timeoutProperties.getMaxBatchesPerRun();
             batchIndex++) {

            List<ExpiredProcessingSubscriptionTarget> targets =
                    findNextBatch(
                            now,
                            lastReservationExpiresAt,
                            lastSubscriptionId
                    );

            if (targets.isEmpty()) {
                break;
            }

            /*
             * 각 청약을 REQUIRES_NEW 트랜잭션으로 처리한다.
             *
             * 한 건이 실패해도 다음 청약을 계속 처리하므로
             * 전체 배치가 함께 롤백되지 않는다.
             */
            for (ExpiredProcessingSubscriptionTarget target : targets) {
                try {
                    boolean processed =
                            subscriptionTimeoutTransactionService
                                    .processExpiredReservation(
                                            target.subscriptionId(),
                                            now
                                    );

                    if (processed) {
                        processedCount++;
                    }
                } catch (Exception e) {
                    offeringSchedulerMetrics
                            .recordSubscriptionTimeoutItemFailure();

                    /*
                     * 현재 대상이 실패해도 키셋 커서를 전진시켜
                     * 같은 실행에서 후속 청약을 계속 처리한다.
                     *
                     * 실패한 청약은 PROCESSING 상태로 남아
                     * 다음 스케줄 실행에서 다시 조회된다.
                     */
                    log.error(
                            "예약 만료 청약 처리 실패. subscriptionId={}",
                            target.subscriptionId(),
                            e
                    );
                }
            }

            /*
             * 처리 성공 여부와 관계없이 조회한 마지막 대상을
             * 다음 키셋 조회의 커서로 사용한다.
             */
            ExpiredProcessingSubscriptionTarget lastTarget =
                    targets.get(targets.size() - 1);

            lastReservationExpiresAt =
                    lastTarget.reservationExpiresAt();

            lastSubscriptionId =
                    lastTarget.subscriptionId();

            /*
             * 설정된 배치 크기보다 적게 조회됐다면
             * 현재 기준 시각의 후속 대상이 없으므로 종료한다.
             */
            if (targets.size() < timeoutProperties.getBatchSize()) {
                break;
            }
        }

        return processedCount;
    }

    private List<ExpiredProcessingSubscriptionTarget> findNextBatch(
            Instant now,
            Instant lastReservationExpiresAt,
            UUID lastSubscriptionId
    ) {
        PageRequest pageRequest = PageRequest.of(
                0,
                timeoutProperties.getBatchSize()
        );

        if (lastReservationExpiresAt == null) {
            return subscriptionRepository
                    .findExpiredProcessingSubscriptionTargets(
                            now,
                            pageRequest
                    );
        }

        return subscriptionRepository
                .findExpiredProcessingSubscriptionTargetsAfter(
                        now,
                        lastReservationExpiresAt,
                        lastSubscriptionId,
                        pageRequest
                );
    }
}