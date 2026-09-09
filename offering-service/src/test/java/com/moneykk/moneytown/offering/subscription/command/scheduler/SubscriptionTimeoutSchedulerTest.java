package com.moneykk.moneytown.offering.subscription.command.scheduler;

import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionTimeoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionTimeoutSchedulerTest {

    private static final int MAX_BATCHES_PER_RUN = 3;

    @Mock
    private SubscriptionTimeoutService subscriptionTimeoutService;

    @InjectMocks
    private SubscriptionTimeoutScheduler subscriptionTimeoutScheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                subscriptionTimeoutScheduler,
                "maxBatchesPerRun",
                MAX_BATCHES_PER_RUN
        );
    }

    @Test
    @DisplayName("처리할 타임아웃 청약이 없으면 첫 배치 실행 후 종료한다")
    void stopsWhenNoExpiredReservationExists() {
        // given
        when(subscriptionTimeoutService.processExpiredReservations())
                .thenReturn(0);

        // when
        subscriptionTimeoutScheduler.processExpiredReservations();

        // then
        verify(subscriptionTimeoutService, times(1))
                .processExpiredReservations();
    }

    @Test
    @DisplayName("타임아웃 청약이 남아 있으면 빈 배치가 나올 때까지 반복한다")
    void repeatsUntilEmptyBatch() {
        // given
        ReflectionTestUtils.setField(
                subscriptionTimeoutScheduler,
                "maxBatchesPerRun",
                5
        );

        when(subscriptionTimeoutService.processExpiredReservations())
                .thenReturn(100, 20, 0);

        // when
        subscriptionTimeoutScheduler.processExpiredReservations();

        // then
        verify(subscriptionTimeoutService, times(3))
                .processExpiredReservations();
    }

    @Test
    @DisplayName("타임아웃 청약이 계속 남아 있어도 설정된 최대 배치 횟수까지만 처리한다")
    void stopsAtMaximumBatchCount() {
        // given
        when(subscriptionTimeoutService.processExpiredReservations())
                .thenReturn(100);

        // when
        subscriptionTimeoutScheduler.processExpiredReservations();

        // then
        verify(subscriptionTimeoutService, times(MAX_BATCHES_PER_RUN))
                .processExpiredReservations();
    }

    @Test
    @DisplayName("배치 처리 중 예외가 발생해도 스케줄러 밖으로 전파하지 않는다")
    void doesNotPropagateBatchFailure() {
        // given
        when(subscriptionTimeoutService.processExpiredReservations())
                .thenReturn(100)
                .thenThrow(new RuntimeException("DB 오류"));

        // when & then
        assertThatCode(() ->
                subscriptionTimeoutScheduler.processExpiredReservations()
        ).doesNotThrowAnyException();

        verify(subscriptionTimeoutService, times(2))
                .processExpiredReservations();
    }

    @Test
    @DisplayName("최대 배치 횟수 설정이 잘못되면 타임아웃 처리를 실행하지 않는다")
    void doesNotRunWithInvalidMaximumBatchCount() {
        // given
        ReflectionTestUtils.setField(
                subscriptionTimeoutScheduler,
                "maxBatchesPerRun",
                0
        );

        // when
        subscriptionTimeoutScheduler.processExpiredReservations();

        // then
        verifyNoInteractions(subscriptionTimeoutService);
    }
}