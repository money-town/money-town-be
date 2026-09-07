package com.moneykk.moneytown.offering.subscription.command.scheduler;

import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionIdempotencyRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionIdempotencyRecoveryScheduler {

    private final SubscriptionIdempotencyRecoveryService
            subscriptionIdempotencyRecoveryService;

    @Value("${subscription.idempotency.recovery-batch-size:100}")
    private int recoveryBatchSize;

    /**
     * 서버 종료나 처리 중단으로 PROCESSING에 남은
     * 오래된 청약 멱등 요청을 FAILED 상태로 복구한다.
     */
    @Scheduled(
            fixedDelayString =
                    "${subscription.idempotency.recovery-delay-ms:60000}"
    )
    public void recoverExpiredProcessingRequests() {
        try {
            int recovered =
                    subscriptionIdempotencyRecoveryService
                            .recoverExpiredProcessing(recoveryBatchSize);

            if (recovered > 0) {
                log.warn(
                        "처리 기한을 초과한 청약 멱등 요청 복구. count={}",
                        recovered
                );
            }
        } catch (Exception e) {
            /*
             * 한 번의 복구 실패가 스케줄러의 다음 실행을 막지 않도록
             * 예외를 기록하고 종료한다.
             */
            log.error("청약 멱등 요청 PROCESSING 복구 실패", e);
        }
    }
}