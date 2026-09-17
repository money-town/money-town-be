package com.moneykk.moneytown.offering.subscription.command.scheduler;

import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionConfirmationBatchTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionConfirmationBatchScheduler {

    private final SubscriptionConfirmationBatchTransactionService
            subscriptionConfirmationBatchTransactionService;

    /**
     * HOLD_SUCCEEDED 상태로 남아 있는 청약 확정 배치를 처리한다.
     *
     * 한 번 실행할 때 공모 한 건의 청약 한 배치만 처리한다.
     * 각 호출은 SubscriptionConfirmationBatchTransactionService에서
     * 독립된 REQUIRES_NEW 트랜잭션으로 실행된다.
     *
     * 처리 실패 시 예외를 스케줄러 밖으로 전파하지 않는다.
     * 롤백된 HOLD_SUCCEEDED 청약은 다음 실행에서 다시 처리된다.
     */
    @Scheduled(
            fixedDelayString =
                    "${subscription.confirmation.fixed-delay-ms:200}"
    )
    public void confirmNextBatch() {
        try {
            int confirmedCount =
                    subscriptionConfirmationBatchTransactionService
                            .confirmNextBatch();

            if (confirmedCount > 0) {
                log.info(
                        "청약 확정 스케줄 배치 처리 완료. "
                                + "confirmedCount={}",
                        confirmedCount
                );
            }
        } catch (Exception e) {
            /*
             * 현재 배치는 트랜잭션에서 롤백된다.
             * 예외를 전파하지 않아 다음 스케줄 실행에서
             * 남은 HOLD_SUCCEEDED 청약을 다시 처리한다.
             */
            log.error(
                    "청약 확정 스케줄 배치 처리 실패",
                    e
            );
        }
    }
}