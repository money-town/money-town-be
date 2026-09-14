package com.moneykk.moneytown.offering.subscription.command.scheduler;

import com.moneykk.moneytown.offering.offering.command.scheduler.OfferingSchedulerMetrics;
import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionTimeoutService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionTimeoutSchedulerTest {

    @Mock
    private SubscriptionTimeoutService subscriptionTimeoutService;

    @Mock
    private OfferingSchedulerMetrics offeringSchedulerMetrics;

    @InjectMocks
    private SubscriptionTimeoutScheduler subscriptionTimeoutScheduler;

    @Test
    @DisplayName("예약 만료 처리는 스케줄 실행마다 한 번만 요청한다")
    void processesExpiredReservationsOncePerScheduledRun() {
        // given
        when(subscriptionTimeoutService.processExpiredReservations())
                .thenReturn(250);

        // when
        subscriptionTimeoutScheduler.processExpiredReservations();

        // then
        verify(subscriptionTimeoutService)
                .processExpiredReservations();

        /*
         * 키셋 배치 반복은 SubscriptionTimeoutService가 담당하므로
         * Scheduler가 Service를 반복 호출하지 않는지 검증한다.
         */
        verifyNoMoreInteractions(
                subscriptionTimeoutService
        );

        verifyNoInteractions(
                offeringSchedulerMetrics
        );
    }

    @Test
    @DisplayName("처리할 예약 만료 청약이 없어도 정상 종료한다")
    void completesNormallyWhenNoExpiredReservationExists() {
        // given
        when(subscriptionTimeoutService.processExpiredReservations())
                .thenReturn(0);

        // when & then
        assertThatCode(() ->
                subscriptionTimeoutScheduler
                        .processExpiredReservations()
        ).doesNotThrowAnyException();

        verify(subscriptionTimeoutService)
                .processExpiredReservations();

        verifyNoInteractions(
                offeringSchedulerMetrics
        );
    }

    @Test
    @DisplayName("배치 처리 중 예외가 발생해도 스케줄러 밖으로 전파하지 않는다")
    void doesNotPropagateBatchFailure() {
        // given
        when(subscriptionTimeoutService.processExpiredReservations())
                .thenThrow(
                        new RuntimeException("DB 오류")
                );

        // when & then
        assertThatCode(() ->
                subscriptionTimeoutScheduler
                        .processExpiredReservations()
        ).doesNotThrowAnyException();

        verify(subscriptionTimeoutService)
                .processExpiredReservations();

        verify(offeringSchedulerMetrics)
                .recordSubscriptionTimeoutBatchFailure();
    }
}