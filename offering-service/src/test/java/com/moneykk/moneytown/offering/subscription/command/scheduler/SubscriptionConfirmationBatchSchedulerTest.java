package com.moneykk.moneytown.offering.subscription.command.scheduler;

import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionConfirmationBatchTransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionConfirmationBatchSchedulerTest {

    @Mock
    private SubscriptionConfirmationBatchTransactionService
            subscriptionConfirmationBatchTransactionService;

    @InjectMocks
    private SubscriptionConfirmationBatchScheduler scheduler;

    @Test
    @DisplayName(
            "스케줄 실행 시 청약 확정 배치 트랜잭션을 한 번 호출한다"
    )
    void invokesOneConfirmationBatch() {
        // given
        when(subscriptionConfirmationBatchTransactionService
                .confirmNextBatch())
                .thenReturn(100);

        // when
        scheduler.confirmNextBatch();

        // then
        verify(subscriptionConfirmationBatchTransactionService)
                .confirmNextBatch();
    }

    @Test
    @DisplayName(
            "청약 확정 배치가 실패해도 예외를 전파하지 않아 "
                    + "다음 스케줄 실행을 허용한다"
    )
    void doesNotPropagateBatchFailure() {
        // given
        when(subscriptionConfirmationBatchTransactionService
                .confirmNextBatch())
                .thenThrow(
                        new RuntimeException(
                                "confirmation batch failure"
                        )
                );

        // when & then
        assertThatCode(
                scheduler::confirmNextBatch
        ).doesNotThrowAnyException();

        verify(subscriptionConfirmationBatchTransactionService)
                .confirmNextBatch();
    }
}