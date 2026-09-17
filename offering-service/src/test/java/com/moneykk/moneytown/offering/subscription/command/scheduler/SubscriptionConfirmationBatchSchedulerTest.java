package com.moneykk.moneytown.offering.subscription.command.scheduler;

import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionConfirmationBatchException;
import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionConfirmationBatchTransactionService;
import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionConfirmationItemTransactionService;
import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionConfirmationManualReviewService;
import com.moneykk.moneytown.offering.subscription.command.config.SubscriptionConfirmationProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionConfirmationBatchSchedulerTest {

    @Mock
    private SubscriptionConfirmationBatchTransactionService
            subscriptionConfirmationBatchTransactionService;

    @Mock
    private SubscriptionConfirmationItemTransactionService
            subscriptionConfirmationItemTransactionService;

    @Mock
    private SubscriptionConfirmationManualReviewService
            subscriptionConfirmationManualReviewService;

    @Spy
    private SubscriptionConfirmationProperties confirmationProperties =
            new SubscriptionConfirmationProperties();

    @InjectMocks
    private SubscriptionConfirmationBatchScheduler scheduler;

    @Test
    @DisplayName(
            "스케줄 실행당 청약 확정 배치를 최대 두 번 호출한다"
    )
    void invokesAtMostTwoConfirmationBatches() {
        when(subscriptionConfirmationBatchTransactionService
                .confirmNextBatch())
                .thenReturn(100);

        scheduler.confirmNextBatch();

        verify(subscriptionConfirmationBatchTransactionService, times(2))
                .confirmNextBatch();
    }

    @Test
    @DisplayName(
            "처리 대상이 없으면 첫 번째 호출에서 반복을 종료한다"
    )
    void stopsWhenNoConfirmationTargetExists() {
        when(subscriptionConfirmationBatchTransactionService
                .confirmNextBatch())
                .thenReturn(0);

        scheduler.confirmNextBatch();

        verify(subscriptionConfirmationBatchTransactionService)
                .confirmNextBatch();
    }
    @Test
    @DisplayName(
            "대상 ID를 확보하지 못한 배치 실패도 "
                    + "예외를 전파하지 않는다"
    )
    void doesNotPropagateUnknownBatchFailure() {
        when(subscriptionConfirmationBatchTransactionService
                .confirmNextBatch())
                .thenThrow(new RuntimeException(
                        "confirmation batch failure"
                ));

        assertThatCode(
                scheduler::confirmNextBatch
        ).doesNotThrowAnyException();

        verifyNoInteractions(
                subscriptionConfirmationItemTransactionService,
                subscriptionConfirmationManualReviewService
        );
    }

    @Test
    @DisplayName(
            "특정 청약만 데이터 오류가 나면 수동 확인으로 격리하고 "
                    + "다음 청약을 계속 확정한다"
    )
    void isolatesInvalidItemAndContinuesConfirmation() {
        UUID offeringId = UUID.randomUUID();
        UUID failedSubscriptionId = UUID.randomUUID();
        UUID normalSubscriptionId = UUID.randomUUID();

        SubscriptionConfirmationBatchException batchException =
                new SubscriptionConfirmationBatchException(
                        offeringId,
                        List.of(
                                failedSubscriptionId,
                                normalSubscriptionId
                        ),
                        new DataIntegrityViolationException(
                                "batch failure"
                        )
                );

        when(subscriptionConfirmationBatchTransactionService
                .confirmNextBatch())
                .thenThrow(batchException);

        when(subscriptionConfirmationItemTransactionService
                .confirm(offeringId, failedSubscriptionId))
                .thenThrow(new DataIntegrityViolationException(
                        "item failure"
                ));

        when(subscriptionConfirmationManualReviewService
                .markForManualReview(
                        offeringId,
                        failedSubscriptionId
                ))
                .thenReturn(true);

        when(subscriptionConfirmationItemTransactionService
                .confirm(offeringId, normalSubscriptionId))
                .thenReturn(true);

        scheduler.confirmNextBatch();

        verify(subscriptionConfirmationManualReviewService)
                .markForManualReview(
                        offeringId,
                        failedSubscriptionId
                );

        verify(subscriptionConfirmationItemTransactionService)
                .confirm(
                        offeringId,
                        normalSubscriptionId
                );
    }
}
