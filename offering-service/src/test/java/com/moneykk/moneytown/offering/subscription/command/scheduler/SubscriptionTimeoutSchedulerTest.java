package com.moneykk.moneytown.offering.subscription.command.scheduler;

import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionTimeoutService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SubscriptionTimeoutSchedulerTest {

    @Mock
    private SubscriptionTimeoutService subscriptionTimeoutService;

    @InjectMocks
    private SubscriptionTimeoutScheduler subscriptionTimeoutScheduler;

    @Test
    @DisplayName("스케줄러 실행 시 예약 유효시간이 만료된 청약의 timeout 처리를 요청한다")
    void processesExpiredReservations() {
        // when
        subscriptionTimeoutScheduler.processExpiredReservations();

        // then
        verify(subscriptionTimeoutService)
                .processExpiredReservations();
    }
}