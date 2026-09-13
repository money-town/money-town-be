package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionTimeoutServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionTimeoutTransactionService
            subscriptionTimeoutTransactionService;

    @InjectMocks
    private SubscriptionTimeoutService subscriptionTimeoutService;

    @Test
    @DisplayName("예약 만료 처리에 성공한 청약 수만 반환한다")
    void returnsOnlySuccessfullyProcessedCount() {
        // given
        UUID firstSubscriptionId = UUID.randomUUID();
        UUID secondSubscriptionId = UUID.randomUUID();

        when(subscriptionRepository
                .findExpiredProcessingSubscriptionIds(
                        any(Instant.class),
                        any(Pageable.class)
                ))
                .thenReturn(List.of(
                        firstSubscriptionId,
                        secondSubscriptionId
                ));

        when(subscriptionTimeoutTransactionService
                .processExpiredReservation(
                        eq(firstSubscriptionId),
                        any(Instant.class)
                ))
                .thenReturn(true);

        /*
         * ID 조회 후 다른 비동기 처리에서 상태가 변경된 상황을 표현한다.
         * Transaction Service가 false를 반환하면 처리 건수에 포함하지 않는다.
         */
        when(subscriptionTimeoutTransactionService
                .processExpiredReservation(
                        eq(secondSubscriptionId),
                        any(Instant.class)
                ))
                .thenReturn(false);

        // when
        int result =
                subscriptionTimeoutService
                        .processExpiredReservations();

        // then
        assertThat(result).isEqualTo(1);

        verify(subscriptionTimeoutTransactionService)
                .processExpiredReservation(
                        eq(firstSubscriptionId),
                        any(Instant.class)
                );

        verify(subscriptionTimeoutTransactionService)
                .processExpiredReservation(
                        eq(secondSubscriptionId),
                        any(Instant.class)
                );
    }

    @Test
    @DisplayName("한 청약의 만료 처리가 실패해도 다음 청약을 계속 처리한다")
    void continuesAfterIndividualProcessingFailure() {
        // given
        UUID firstSubscriptionId = UUID.randomUUID();
        UUID failedSubscriptionId = UUID.randomUUID();
        UUID lastSubscriptionId = UUID.randomUUID();

        when(subscriptionRepository
                .findExpiredProcessingSubscriptionIds(
                        any(Instant.class),
                        any(Pageable.class)
                ))
                .thenReturn(List.of(
                        firstSubscriptionId,
                        failedSubscriptionId,
                        lastSubscriptionId
                ));

        when(subscriptionTimeoutTransactionService
                .processExpiredReservation(
                        eq(firstSubscriptionId),
                        any(Instant.class)
                ))
                .thenReturn(true);

        when(subscriptionTimeoutTransactionService
                .processExpiredReservation(
                        eq(failedSubscriptionId),
                        any(Instant.class)
                ))
                .thenThrow(
                        new IllegalStateException(
                                "예약 만료 보상 처리 실패"
                        )
                );

        when(subscriptionTimeoutTransactionService
                .processExpiredReservation(
                        eq(lastSubscriptionId),
                        any(Instant.class)
                ))
                .thenReturn(true);

        // when
        int result =
                subscriptionTimeoutService
                        .processExpiredReservations();

        // then
        assertThat(result).isEqualTo(2);

        /*
         * 중간 청약에서 예외가 발생했어도
         * 마지막 청약까지 호출됐는지를 검증한다.
         */
        verify(subscriptionTimeoutTransactionService)
                .processExpiredReservation(
                        eq(lastSubscriptionId),
                        any(Instant.class)
                );
    }

    @Test
    @DisplayName("예약 만료 대상이 없으면 개별 처리 서비스를 호출하지 않는다")
    void doesNotProcessWhenNoExpiredReservationsExist() {
        // given
        when(subscriptionRepository
                .findExpiredProcessingSubscriptionIds(
                        any(Instant.class),
                        any(Pageable.class)
                ))
                .thenReturn(List.of());

        // when
        int result =
                subscriptionTimeoutService
                        .processExpiredReservations();

        // then
        assertThat(result).isZero();

        verifyNoInteractions(
                subscriptionTimeoutTransactionService
        );
    }
}