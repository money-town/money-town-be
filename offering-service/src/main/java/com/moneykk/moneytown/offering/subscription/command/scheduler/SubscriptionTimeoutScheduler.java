package com.moneykk.moneytown.offering.subscription.command.scheduler;

import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionTimeoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubscriptionTimeoutScheduler {

    private final SubscriptionTimeoutService subscriptionTimeoutService;

    /**
     * 예약 유효시간이 만료된 PROCESSING 청약을 탐지하여
     * timeout 보상을 위한 COMPENSATING 상태로 전환한다.
     *
     * 1분마다 실행되며, 실제 상태 전이 규칙은 Subscription 도메인에서 검증한다.
     */
    @Scheduled(cron = "0 * * * * *")
    public void processExpiredReservations() {
        subscriptionTimeoutService.processExpiredReservations();
    }
}
