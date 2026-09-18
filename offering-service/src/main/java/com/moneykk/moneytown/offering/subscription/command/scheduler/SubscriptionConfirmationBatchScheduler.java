package com.moneykk.moneytown.offering.subscription.command.scheduler;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionConfirmationBatchException;
import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionConfirmationBatchTransactionService;
import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionConfirmationItemTransactionService;
import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionConfirmationManualReviewService;
import com.moneykk.moneytown.offering.subscription.command.config.SubscriptionConfirmationProperties;
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
public class SubscriptionConfirmationBatchScheduler {

    private final SubscriptionConfirmationBatchTransactionService
            subscriptionConfirmationBatchTransactionService;

    private final SubscriptionConfirmationItemTransactionService
            subscriptionConfirmationItemTransactionService;

    private final SubscriptionConfirmationManualReviewService
            subscriptionConfirmationManualReviewService;

    private final SubscriptionConfirmationProperties
            confirmationProperties;

    /**
     * HOLD_SUCCEEDED 상태로 남아 있는 청약 확정 배치를 처리한다.
     *
     * 정상 배치가 실패하면 롤백된 대상만 건별 독립 트랜잭션으로
     * 재처리한다. 특정 청약의 데이터 오류는 MANUAL_REVIEW로
     * 격리하고, 시스템 장애는 상태를 변경하지 않고 다음 주기에
     * 다시 시도한다.
     */
    @Scheduled(
            fixedDelayString =
                    "#{@subscriptionConfirmationProperties.fixedDelayMs}"
    )
    public void confirmNextBatch() {
        if (confirmationProperties.getMaxBatchesPerRun() <= 0) {
            log.error(
                    "청약 확정 최대 배치 실행 횟수가 올바르지 않습니다. "
                            + "maxBatchesPerRun={}",
                    confirmationProperties.getMaxBatchesPerRun()
            );
            return;
        }

        int processedBatchCount = 0;
        int totalConfirmedCount = 0;

        for (int index = 0;
             index < confirmationProperties.getMaxBatchesPerRun();
             index++) {
            try {
                int confirmedCount =
                        subscriptionConfirmationBatchTransactionService
                                .confirmNextBatch();

                if (confirmedCount == 0) {
                    break;
                }

                processedBatchCount++;
                totalConfirmedCount += confirmedCount;
            } catch (SubscriptionConfirmationBatchException e) {
                log.warn(
                        "청약 확정 배치 실패. "
                                + "건별 격리 처리를 시작합니다. "
                                + "offeringId={}, batchSize={}",
                        e.getOfferingId(),
                        e.getSubscriptionIds().size(),
                        e
                );

                recoverBatchIndividually(e);
                return;
            } catch (Exception e) {
                log.error(
                        "청약 확정 스케줄 배치 처리 실패",
                        e
                );
                return;
            }
        }

        if (totalConfirmedCount > 0) {
            log.info(
                    "청약 확정 스케줄 처리 완료. "
                            + "processedBatchCount={}, "
                            + "totalConfirmedCount={}, "
                            + "maxBatchesPerRun={}",
                    processedBatchCount,
                    totalConfirmedCount,
                    confirmationProperties.getMaxBatchesPerRun()
            );
        }
    }

    private void recoverBatchIndividually(
            SubscriptionConfirmationBatchException batchException
    ) {
        UUID offeringId = batchException.getOfferingId();

        int confirmedCount = 0;
        int manualReviewCount = 0;

        for (UUID subscriptionId
                : batchException.getSubscriptionIds()) {
            try {
                boolean confirmed =
                        subscriptionConfirmationItemTransactionService
                                .confirm(
                                        offeringId,
                                        subscriptionId
                                );

                if (confirmed) {
                    confirmedCount++;
                }
            } catch (Exception itemException) {
                if (isRetryableSystemFailure(itemException)) {
                    log.error(
                            "청약 건별 확정 중 재시도 가능한 시스템 장애 발생. "
                                    + "offeringId={}, subscriptionId={}",
                            offeringId,
                            subscriptionId,
                            itemException
                    );
                    return;
                }

                if (!isIsolatableItemFailure(itemException)) {
                    log.error(
                            "청약 건별 확정 중 격리 여부를 판단할 수 없는 오류 발생. "
                                    + "offeringId={}, subscriptionId={}",
                            offeringId,
                            subscriptionId,
                            itemException
                    );
                    return;
                }

                try {
                    boolean marked =
                            subscriptionConfirmationManualReviewService
                                    .markForManualReview(
                                            offeringId,
                                            subscriptionId
                                    );

                    if (marked) {
                        manualReviewCount++;
                    }
                } catch (Exception manualReviewException) {
                    log.error(
                            "확정 실패 청약의 MANUAL_REVIEW 전환 실패. "
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
                "청약 확정 실패 배치 건별 처리 완료. "
                        + "offeringId={}, confirmedCount={}, "
                        + "manualReviewCount={}",
                offeringId,
                confirmedCount,
                manualReviewCount
        );
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
