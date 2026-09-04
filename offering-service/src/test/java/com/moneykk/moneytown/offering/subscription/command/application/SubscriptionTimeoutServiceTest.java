package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionTimeoutServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private SubscriptionTimeoutService subscriptionTimeoutService;

    @Test
    @DisplayName("예약 유효시간이 만료된 PROCESSING 청약을 COMPENSATING 상태로 전환한다")
    void processesExpiredReservations() {
        // given
        Subscription subscription1 = mock(Subscription.class);
        Subscription subscription2 = mock(Subscription.class);

        when(subscriptionRepository
                .findAllBySubscriptionStatusAndReservationExpiresAtLessThanEqualAndIsDeletedFalse(
                        eq(SubscriptionStatus.PROCESSING),
                        any(),
                        any()
                ))
                .thenReturn(List.of(
                        subscription1,
                        subscription2
                ));

        // when
        int result =
                subscriptionTimeoutService.processExpiredReservations();

        // then
        assertThat(result).isEqualTo(2);

        verify(subscription1)
                .startExpirationCompensation(any());

        verify(subscription2)
                .startExpirationCompensation(any());
    }

    @Test
    @DisplayName("예약 유효시간이 만료된 청약이 없으면 처리 건수 0을 반환한다")
    void returnsZeroWhenNoExpiredReservations() {
        // given
        when(subscriptionRepository
                .findAllBySubscriptionStatusAndReservationExpiresAtLessThanEqualAndIsDeletedFalse(
                        eq(SubscriptionStatus.PROCESSING),
                        any(),
                        any()
                ))
                .thenReturn(List.of());

        // when
        int result =
                subscriptionTimeoutService.processExpiredReservations();

        // then
        assertThat(result).isZero();
    }
}