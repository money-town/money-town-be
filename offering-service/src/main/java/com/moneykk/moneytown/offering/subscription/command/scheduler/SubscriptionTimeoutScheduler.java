package com.moneykk.moneytown.offering.subscription.command.scheduler;

import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionTimeoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionTimeoutScheduler {

    private final SubscriptionTimeoutService subscriptionTimeoutService;

    /**
     * 한 번의 스케줄 실행에서 처리할 최대 배치 횟수.
     *
     * SubscriptionTimeoutService가 배치당 최대 100건을 처리하므로
     * 기본 설정에서는 한 번에 최대 1,000건을 처리한다.
     */
    @Value("${subscription.timeout.max-batches-per-run:10}")
    private int maxBatchesPerRun;

    /**
     * 예약 유효시간이 만료된 PROCESSING 청약을 탐지하여
     * 타임아웃 보상을 위한 COMPENSATING 상태로 전환한다.
     *
     * 한 배치에서 처리할 대상이 없거나 최대 반복 횟수에
     * 도달할 때까지 제한적으로 반복한다.
     */
    @Scheduled(cron = "0 * * * * *")
    public void processExpiredReservations() {
        if (maxBatchesPerRun <= 0) {
            log.error(
                    "타임아웃 처리 최대 배치 횟수는 1 이상이어야 합니다. value={}",
                    maxBatchesPerRun
            );
            return;
        }

        int totalProcessed = 0;

        try {
            for (int batchNumber = 1;
                 batchNumber <= maxBatchesPerRun;
                 batchNumber++) {

                int processed =
                        subscriptionTimeoutService
                                .processExpiredReservations();

                totalProcessed += processed;

                if (processed <= 0) {
                    break;
                }

                if (batchNumber == maxBatchesPerRun) {
                    log.warn(
                            "타임아웃 청약 처리 최대 배치 횟수 도달. "
                                    + "processed={}, maxBatchesPerRun={}",
                            totalProcessed,
                            maxBatchesPerRun
                    );
                }
            }

            if (totalProcessed > 0) {
                log.info(
                        "타임아웃 청약 처리 완료. processed={}",
                        totalProcessed
                );
            }
        } catch (Exception e) {
            /*
             * 현재 실행은 중단하지만 예외를 전파하지 않아
             * 다음 스케줄에서 남은 청약을 다시 처리할 수 있게 한다.
             */
            log.error(
                    "타임아웃 청약 배치 처리 실패. processed={}",
                    totalProcessed,
                    e
            );
        }
    }
}
