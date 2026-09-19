package com.moneykk.moneytown.offering.offering.command.scheduler;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.offering.command.application.OfferingCancellationBatchException;
import com.moneykk.moneytown.offering.offering.command.application.OfferingCancellationBatchTransactionService;
import com.moneykk.moneytown.offering.offering.command.application.OfferingCancellationItemTransactionService;
import com.moneykk.moneytown.offering.offering.command.application.OfferingCancellationManualReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OfferingCancellationBatchScheduler {

    private final OfferingCancellationBatchTransactionService
            offeringCancellationBatchTransactionService;

    private final OfferingCancellationItemTransactionService
            offeringCancellationItemTransactionService;

    private final OfferingCancellationManualReviewService
            offeringCancellationManualReviewService;

    private final OfferingSchedulerMetrics offeringSchedulerMetrics;

    /**
     * 취소 처리 중인 공모의 보상 대상 청약을 배치 처리한다.
     *
     * 정상 상황에서는 청약 여러 건을 하나의 트랜잭션으로 처리한다.
     * 배치 처리에 실패하면 해당 배치의 청약만 건별 독립
     * 트랜잭션으로 재처리하여 실패 청약을 격리한다.
     */
    @Scheduled(
            fixedDelayString =
                    "${offering.cancellation.fixed-delay-ms:1000}"
    )
    public void compensateNextBatch() {
        try {
            int compensatedCount =
                    offeringCancellationBatchTransactionService
                            .compensateNextBatch();

            if (compensatedCount > 0) {
                log.info(
                        "공모 취소 보상 스케줄 배치 처리 완료. "
                                + "compensatedCount={}",
                        compensatedCount
                );
            }
        } catch (OfferingCancellationBatchException e) {
            log.warn(
                    "공모 취소 보상 배치 실패. "
                            + "건별 격리 처리를 시작합니다. "
                            + "offeringId={}, batchSize={}",
                    e.getOfferingId(),
                    e.getSubscriptionIds().size(),
                    e
            );

            recoverBatchIndividually(e);
        } catch (Exception e) {
            /*
             * 배치 대상을 조회하기 전 발생한 장애 등
             * 실패 청약 ID를 확보하지 못한 경우다.
             *
             * 상태를 변경하지 않고 다음 스케줄 실행에서 재시도한다.
             */
            offeringSchedulerMetrics
                    .recordOfferingCancellationBatchFailure();

            log.error(
                    "공모 취소 보상 스케줄 실행 실패",
                    e
            );
        }
    }

    /**
     * 롤백된 배치의 청약을 한 건씩 독립 트랜잭션으로 처리한다.
     *
     * 특정 청약의 데이터 오류는 MANUAL_REVIEW로 격리하고,
     * DB 연결 실패와 같은 시스템 장애는 상태를 변경하지 않고
     * 건별 복구를 중단한다.
     */
    private void recoverBatchIndividually(
            OfferingCancellationBatchException batchException
    ) {
        UUID offeringId = batchException.getOfferingId();

        int compensatedCount = 0;
        int manualReviewCount = 0;

        for (UUID subscriptionId
                : batchException.getSubscriptionIds()) {
            try {
                boolean compensated =
                        offeringCancellationItemTransactionService
                                .compensate(
                                        offeringId,
                                        subscriptionId
                                );

                if (compensated) {
                    compensatedCount++;
                }
            } catch (Exception itemException) {
                if (isRetryableSystemFailure(itemException)) {
                    /*
                     * DB 연결 실패, lock timeout 등은 특정 청약의
                     * 데이터 문제가 아니므로 MANUAL_REVIEW로
                     * 전환하지 않는다.
                     *
                     * 남은 청약도 같은 장애를 겪을 가능성이 높으므로
                     * 현재 건별 복구를 중단하고 다음 주기에 재시도한다.
                     */
                    log.error(
                            "공모 취소 건별 보상 중 "
                                    + "재시도 가능한 시스템 장애 발생. "
                                    + "offeringId={}, subscriptionId={}",
                            offeringId,
                            subscriptionId,
                            itemException
                    );

                    return;
                }

                if (!isIsolatableItemFailure(itemException)) {
                    /*
                     * Outbox 직렬화 설정 오류와 같이 모든 청약에
                     * 영향을 줄 수 있는 알 수 없는 오류는 대량의
                     * MANUAL_REVIEW 전환을 막기 위해 격리하지 않는다.
                     */
                    log.error(
                            "공모 취소 건별 보상 중 "
                                    + "격리 여부를 판단할 수 없는 오류 발생. "
                                    + "offeringId={}, subscriptionId={}",
                            offeringId,
                            subscriptionId,
                            itemException
                    );

                    return;
                }

                try {
                    boolean marked =
                            offeringCancellationManualReviewService
                                    .markForManualReview(
                                            offeringId,
                                            subscriptionId
                                    );

                    if (marked) {
                        manualReviewCount++;
                    }
                } catch (Exception manualReviewException) {
                    /*
                     * 수동 확인 전환 자체가 실패하면 상태를 억지로
                     * 변경하지 않는다. 다음 스케줄 실행에서 다시
                     * 배치 대상으로 조회될 수 있도록 그대로 둔다.
                     */
                    log.error(
                            "공모 취소 실패 청약의 "
                                    + "MANUAL_REVIEW 전환 실패. "
                                    + "offeringId={}, subscriptionId={}",
                            offeringId,
                            subscriptionId,
                            manualReviewException
                    );

                    return;
                }
            }
        }

        log.info(
                "공모 취소 실패 배치 건별 처리 완료. "
                        + "offeringId={}, compensatedCount={}, "
                        + "manualReviewCount={}",
                offeringId,
                compensatedCount,
                manualReviewCount
        );
    }

    /**
     * 시간이 지난 뒤 다시 시도할 수 있는 시스템 장애인지 확인한다.
     */
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

    /**
     * 특정 청약만의 상태 또는 데이터 문제로 판단할 수 있는지 확인한다.
     *
     * 알 수 없는 예외를 모두 수동 확인으로 보내면 시스템 설정 장애로
     * 수천 건이 동시에 MANUAL_REVIEW가 될 수 있으므로 범위를 제한한다.
     */
    private boolean isIsolatableItemFailure(
            Throwable throwable
    ) {
        Throwable current = throwable;

        while (current != null) {
            if (current instanceof BusinessException
                    || current
                    instanceof DataIntegrityViolationException) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }
}
