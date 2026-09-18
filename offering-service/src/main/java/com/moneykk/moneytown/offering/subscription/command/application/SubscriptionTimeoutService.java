package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.offering.offering.command.scheduler.OfferingSchedulerMetrics;
import com.moneykk.moneytown.offering.subscription.command.config.SubscriptionTimeoutProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionTimeoutService {

    private final OfferingSchedulerMetrics offeringSchedulerMetrics;

    private final SubscriptionTimeoutBatchTransactionService
            subscriptionTimeoutBatchTransactionService;

    /*
     * 청약 한 건을 독립 트랜잭션으로 처리하는 서비스를 주입한다.
     */
    private final SubscriptionTimeoutTransactionService
            subscriptionTimeoutTransactionService;

    private final SubscriptionTimeoutProperties timeoutProperties;

    /**
     * 예약 유효시간이 만료된 PROCESSING 청약을
     * 공모 단위로 선점하고 설정된 크기의 배치로 보상 처리를 시작한다.
     *
     * 한 번의 스케줄 실행에서 처리하는 최대 배치 수를 제한하여
     * 예약 만료 backlog가 많아도 스케줄러 스레드와 DB 커넥션을
     * 장시간 독점하지 않게 한다.
     *
     * 정상 경로에서는 공모 한 건의 청약 한 배치를 하나의 독립
     * 트랜잭션으로 처리한다. 여러 인스턴스는 SKIP LOCKED로 서로 다른
     * 공모를 선점하므로 같은 후보 목록을 중복 조회하지 않는다.
     *
     * 처리하지 못한 청약과 실행 한도를 초과한 청약은
     * 다음 스케줄 실행에서 다시 조회된다.
     *
     * @return 실제로 만료 보상을 시작한 청약 수
     */
    public int processExpiredReservations() {

        Instant now = Instant.now();

        int processedCount = 0;

        for (int batchIndex = 0;
             batchIndex < timeoutProperties.getMaxBatchesPerRun();
             batchIndex++) {

            try {
                int batchProcessedCount =
                        subscriptionTimeoutBatchTransactionService
                                .processNextBatch(
                                        now,
                                        timeoutProperties.getBatchSize()
                                );

                if (batchProcessedCount == 0) {
                    break;
                }

                processedCount += batchProcessedCount;
            } catch (SubscriptionTimeoutBatchException e) {
                /*
                 * 정상 배치는 전부 롤백됐다. 실패 배치에 포함된 청약을
                 * 기존 건별 REQUIRES_NEW 경로로 재처리하여 특정 청약의
                 * 실패가 나머지 청약을 막지 않게 한다.
                 */
                processedCount += recoverBatchIndividually(e, now);
            } catch (Exception e) {
                /*
                 * 대상 ID를 확보하기 전에 발생한 시스템 장애는 현재
                 * 실행을 중단하고 다음 스케줄 실행에서 재시도한다.
                 */
                offeringSchedulerMetrics
                        .recordSubscriptionTimeoutBatchFailure();

                log.error(
                        "예약 만료 배치 선점 또는 처리 실패",
                        e
                );
                break;
            }
        }

        return processedCount;
    }

    private int recoverBatchIndividually(
            SubscriptionTimeoutBatchException batchException,
            Instant now
    ) {
        int processedCount = 0;

        log.warn(
                "예약 만료 배치 실패. 건별 재처리를 시작합니다. "
                        + "offeringId={}, batchSize={}",
                batchException.getOfferingId(),
                batchException.getSubscriptionIds().size(),
                batchException
        );

        for (UUID subscriptionId
                : batchException.getSubscriptionIds()) {
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
                offeringSchedulerMetrics
                        .recordSubscriptionTimeoutItemFailure();

                log.error(
                        "예약 만료 청약 건별 처리 실패. "
                                + "offeringId={}, subscriptionId={}",
                        batchException.getOfferingId(),
                        subscriptionId,
                        e
                );
            }
        }

        return processedCount;
    }
}
