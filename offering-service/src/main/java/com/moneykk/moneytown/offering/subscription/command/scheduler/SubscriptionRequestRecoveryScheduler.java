package com.moneykk.moneytown.offering.subscription.command.scheduler;

import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionRequestRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionRequestRecoveryScheduler {

    private final SubscriptionRequestRecoveryService recoveryService;

    @Scheduled(fixedDelayString =
            "${subscription.request.recovery.fixed-delay-ms:60000}")
    public void recover() {
        try {
            int recovered = recoveryService.recoverStuckRequests();
            if (recovered > 0) {
                log.warn("정체된 비동기 청약 접수 복구. count={}", recovered);
            }
        } catch (Exception e) {
            log.error("비동기 청약 접수 복구 실패", e);
        }
    }
}
