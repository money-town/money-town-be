package com.moneykk.moneytown.offering.subscription.command.scheduler;

import com.moneykk.moneytown.offering.offering.command.scheduler.OfferingSchedulerMetrics;
import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionTimeoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionTimeoutScheduler {

    private final SubscriptionTimeoutService subscriptionTimeoutService;
    private final OfferingSchedulerMetrics offeringSchedulerMetrics;

    /**
     * 예약 유효시간이 만료된 PROCESSING 청약을 탐지하여
     * 타임아웃 보상을 위한 COMPENSATING 상태로 전환한다.
     *
     * SubscriptionTimeoutService가 키셋 방식으로 대상을 조회하고,
     * 설정된 최대 배치 수까지만 처리한다.
     *
     * 개별 청약의 처리 실패와 실행 한도를 초과한 청약은
     * 다음 스케줄 실행에서 다시 처리한다.
     */
    @Scheduled(cron = "0 * * * * *")
    public void processExpiredReservations() {
        try {
            int processed =
                    subscriptionTimeoutService
                            .processExpiredReservations();

            if (processed > 0) {
                log.info(
                        "타임아웃 청약 처리 완료. processed={}",
                        processed
                );
            }
        } catch (Exception e) {
            offeringSchedulerMetrics
                    .recordSubscriptionTimeoutBatchFailure();

            /*
             * 현재 실행은 중단하지만 예외를 전파하지 않아
             * 다음 스케줄에서 남은 청약을 다시 처리할 수 있게 한다.
             */
            log.error(
                    "타임아웃 청약 배치 처리 실패",
                    e
            );
        }
    }
}