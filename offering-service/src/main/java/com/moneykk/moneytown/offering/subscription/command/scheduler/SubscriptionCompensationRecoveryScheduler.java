package com.moneykk.moneytown.offering.subscription.command.scheduler;

import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionCompensationRecoveryBatchException;
import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionCompensationRecoveryTransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class SubscriptionCompensationRecoveryScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(
                    SubscriptionCompensationRecoveryScheduler.class
            );

    private static final long DEFAULT_STUCK_SECONDS = 300L;
    private static final int DEFAULT_BATCH_SIZE = 100;

    private final SubscriptionCompensationRecoveryTransactionService
            recoveryTransactionService;

    @Value(
            "${subscription.compensation.recovery.stuck-seconds:300}"
    )
    private long stuckSeconds = DEFAULT_STUCK_SECONDS;

    @Value(
            "${subscription.compensation.recovery.batch-size:100}"
    )
    private int batchSize = DEFAULT_BATCH_SIZE;

    public SubscriptionCompensationRecoveryScheduler(
            SubscriptionCompensationRecoveryTransactionService
                    recoveryTransactionService
    ) {
        this.recoveryTransactionService =
                recoveryTransactionService;
    }

    /**
     * Wallet 또는 Holding 결과가 일정 시간 이상 완료되지 않은
     * COMPENSATING 청약을 수동 확인 대상으로 격리한다.
     *
     * 정상 경로에서는 공모를 SKIP LOCKED로 선점하고 청약 한 배치를
     * Offering → Subscription → Compensation 순서로 잠가 처리한다.
     * 배치가 실패하면 포함된 청약만 건별 독립 트랜잭션으로 재처리한다.
     */
    @Scheduled(
            fixedDelayString =
                    "${subscription.compensation.recovery.fixed-delay-ms:30000}"
    )
    public void recoverStuckCompensations() {
        if (!hasValidConfiguration()) {
            log.error(
                    "보상 복구 스케줄 설정이 올바르지 않습니다. "
                            + "stuckSeconds={}, batchSize={}",
                    stuckSeconds,
                    batchSize
            );
            return;
        }

        Instant stuckBefore =
                Instant.now().minusSeconds(stuckSeconds);

        try {
            int manualReviewCount = recoveryTransactionService
                    .markNextStuckBatchForManualReview(
                            stuckBefore,
                            batchSize
                    );

            if (manualReviewCount > 0) {
                log.warn(
                        "장기 미완료 보상 수동 확인 전환 완료. "
                                + "manualReviewCount={}",
                        manualReviewCount
                );
            }
        } catch (SubscriptionCompensationRecoveryBatchException e) {
            log.warn(
                    "장기 미완료 보상 복구 배치 실패. "
                            + "건별 재처리를 시작합니다. "
                            + "offeringId={}, batchSize={}",
                    e.getOfferingId(),
                    e.getSubscriptionIds().size(),
                    e
            );

            recoverIndividually(
                    e.getSubscriptionIds(),
                    stuckBefore
            );
        } catch (Exception e) {
            log.error(
                    "장기 미완료 보상 대상 선점 또는 배치 처리 실패",
                    e
            );
        }
    }

    private boolean hasValidConfiguration() {
        return stuckSeconds > 0 && batchSize > 0;
    }

    /**
     * 후보 청약을 각각 독립된 트랜잭션으로 처리한다.
     *
     * 특정 청약의 데이터 문제는 기록하고 다음 청약을 처리한다.
     * DB 연결 실패나 트랜잭션 타임아웃이면 남은 작업도
     * 같은 실패를 겪을 가능성이 높으므로 현재 실행을 종료한다.
     */
    private int recoverIndividually(
            List<UUID> subscriptionIds,
            Instant stuckBefore
    ) {
        int manualReviewCount = 0;

        for (UUID subscriptionId : subscriptionIds) {
            try {
                boolean marked =
                        recoveryTransactionService
                                .markStuckForManualReview(
                                        subscriptionId,
                                        stuckBefore
                                );

                if (marked) {
                    manualReviewCount++;
                }
            } catch (Exception e) {
                if (isRetryableSystemFailure(e)) {
                    log.error(
                            "장기 미완료 보상 격리 중 시스템 장애 발생. "
                                    + "다음 실행에서 재시도합니다. "
                                    + "subscriptionId={}",
                            subscriptionId,
                            e
                    );
                    break;
                }

                /*
                 * 특정 청약의 데이터 문제로 전체 후보 처리를
                 * 중단하지 않는다.
                 */
                log.error(
                        "장기 미완료 보상 격리 실패. "
                                + "subscriptionId={}",
                        subscriptionId,
                        e
                );
            }
        }

        return manualReviewCount;
    }

    private boolean isRetryableSystemFailure(
            Throwable throwable
    ) {
        Throwable current = throwable;

        while (current != null) {
            if (current instanceof TransientDataAccessException
                    || current
                    instanceof CannotCreateTransactionException
                    || current
                    instanceof TransactionTimedOutException) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }
}
