package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.offering.offering.command.scheduler.OfferingSchedulerMetrics;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
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

    private static final int TIMEOUT_BATCH_SIZE = 100;

    private final SubscriptionRepository subscriptionRepository;
    private final OfferingSchedulerMetrics offeringSchedulerMetrics;

    /*
     * 청약 한 건을 독립 트랜잭션으로 처리하는 서비스를 주입한다.
     */
    private final SubscriptionTimeoutTransactionService
            subscriptionTimeoutTransactionService;

    /**
     * 예약 유효시간이 만료된 PROCESSING 청약을 조회하여
     * 청약별로 독립된 트랜잭션에서 보상 처리를 시작한다.
     *
     * 특정 청약의 처리에 실패해도 나머지 청약은 계속 처리한다.
     *
     * @return 실제로 만료 보상을 시작한 청약 수
     */
    public int processExpiredReservations() {

        Instant now = Instant.now();

        /*
         * 청약 엔티티와 잠금을 한 번에 조회하지 않고
         * 처리 대상 ID만 조회한다.
         */
        List<UUID> subscriptionIds =
                subscriptionRepository
                        .findExpiredProcessingSubscriptionIds(
                                now,
                                PageRequest.of(
                                        0,
                                        TIMEOUT_BATCH_SIZE
                                )
                        );

        int processedCount = 0;

        /*
         * 각 청약을 REQUIRES_NEW 트랜잭션으로 처리한다.
         *
         * 한 건이 실패해도 catch 후 다음 청약을 계속 처리하므로
         * 전체 배치가 함께 롤백되지 않는다.
         */
        for (UUID subscriptionId : subscriptionIds) {
            try {
                boolean processed =
                        subscriptionTimeoutTransactionService
                                .processExpiredReservation(
                                        subscriptionId,
                                        now
                                );

                if (processed) {
                    processedCount++;
                }

            } catch (Exception e) {

                offeringSchedulerMetrics.recordSubscriptionTimeoutItemFailure();
                /*
                 * 실패한 청약 ID와 예외를 명시적으로 기록한다.
                 *
                 * 실패한 청약은 PROCESSING 상태로 남기 때문에
                 * 다음 스케줄 실행에서 다시 조회된다.
                 */
                log.error(
                        "예약 만료 청약 처리 실패. subscriptionId={}",
                        subscriptionId,
                        e
                );
            }
        }

        return processedCount;
    }
}