package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.offering.offering.command.scheduler.OfferingSchedulerMetrics;
import com.moneykk.moneytown.offering.subscription.command.config.SubscriptionTimeoutProperties;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.projection.ExpiredProcessingSubscriptionTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.Mockito.never;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

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

    @Mock
    private OfferingSchedulerMetrics offeringSchedulerMetrics;

    private SubscriptionTimeoutProperties timeoutProperties;

    private SubscriptionTimeoutService subscriptionTimeoutService;

    @BeforeEach
    void setUp() {
        timeoutProperties = new SubscriptionTimeoutProperties();
        timeoutProperties.setBatchSize(100);
        timeoutProperties.setMaxBatchesPerRun(2);

        subscriptionTimeoutService =
                new SubscriptionTimeoutService(
                        subscriptionRepository,
                        offeringSchedulerMetrics,
                        subscriptionTimeoutTransactionService,
                        timeoutProperties
                );
    }

    @Test
    @DisplayName("예약 만료 처리에 성공한 청약 수만 반환한다")
    void returnsOnlySuccessfullyProcessedCount() {
        // given
        UUID firstSubscriptionId = UUID.randomUUID();
        UUID secondSubscriptionId = UUID.randomUUID();

        Instant reservationExpiresAt =
                Instant.parse("2026-09-01T00:00:00Z");

        when(subscriptionRepository
                .findExpiredProcessingSubscriptionTargets(
                        any(Instant.class),
                        any(Pageable.class)
                ))
                .thenReturn(List.of(
                        new ExpiredProcessingSubscriptionTarget(
                                firstSubscriptionId,
                                reservationExpiresAt
                        ),
                        new ExpiredProcessingSubscriptionTarget(
                                secondSubscriptionId,
                                reservationExpiresAt
                        )
                ));

        when(subscriptionTimeoutTransactionService
                .processExpiredReservation(
                        eq(firstSubscriptionId),
                        any(Instant.class)
                ))
                .thenReturn(true);

        /*
         * 대상 조회 후 다른 비동기 처리에서 상태가 변경된 상황이다.
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

        Instant reservationExpiresAt =
                Instant.parse("2026-09-01T00:00:00Z");

        when(subscriptionRepository
                .findExpiredProcessingSubscriptionTargets(
                        any(Instant.class),
                        any(Pageable.class)
                ))
                .thenReturn(List.of(
                        new ExpiredProcessingSubscriptionTarget(
                                firstSubscriptionId,
                                reservationExpiresAt
                        ),
                        new ExpiredProcessingSubscriptionTarget(
                                failedSubscriptionId,
                                reservationExpiresAt
                        ),
                        new ExpiredProcessingSubscriptionTarget(
                                lastSubscriptionId,
                                reservationExpiresAt
                        )
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
         * 중간 청약에서 예외가 발생해도
         * 마지막 청약까지 처리했는지 검증한다.
         */
        verify(subscriptionTimeoutTransactionService)
                .processExpiredReservation(
                        eq(lastSubscriptionId),
                        any(Instant.class)
                );

        verify(offeringSchedulerMetrics)
                .recordSubscriptionTimeoutItemFailure();
    }

    @Test
    @DisplayName("예약 만료 대상이 없으면 개별 처리 서비스를 호출하지 않는다")
    void doesNotProcessWhenNoExpiredReservationsExist() {
        // given
        when(subscriptionRepository
                .findExpiredProcessingSubscriptionTargets(
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

    @Test
    @DisplayName("첫 배치가 처리되지 않아도 다음 키셋 배치를 계속 처리한다")
    void continuesWithNextKeysetBatchWhenFirstBatchIsNotProcessed() {
        // given
        Instant firstReservationExpiresAt =
                Instant.parse("2026-09-01T00:00:00Z");

        Instant nextReservationExpiresAt =
                Instant.parse("2026-09-02T00:00:00Z");

        List<ExpiredProcessingSubscriptionTarget> firstBatch =
                IntStream.range(0, 100)
                        .mapToObj(index ->
                                new ExpiredProcessingSubscriptionTarget(
                                        UUID.randomUUID(),
                                        firstReservationExpiresAt
                                )
                        )
                        .sorted(
                                Comparator.comparing(
                                        ExpiredProcessingSubscriptionTarget
                                                ::subscriptionId
                                )
                        )
                        .toList();

        ExpiredProcessingSubscriptionTarget lastTarget =
                firstBatch.get(firstBatch.size() - 1);

        UUID nextSubscriptionId = UUID.randomUUID();

        when(subscriptionRepository
                .findExpiredProcessingSubscriptionTargets(
                        any(Instant.class),
                        any(Pageable.class)
                ))
                .thenReturn(firstBatch);

        when(subscriptionRepository
                .findExpiredProcessingSubscriptionTargetsAfter(
                        any(Instant.class),
                        eq(lastTarget.reservationExpiresAt()),
                        eq(lastTarget.subscriptionId()),
                        any(Pageable.class)
                ))
                .thenReturn(List.of(
                        new ExpiredProcessingSubscriptionTarget(
                                nextSubscriptionId,
                                nextReservationExpiresAt
                        )
                ));

        /*
         * 첫 배치의 대상들은 조회 이후 상태가 변경됐거나
         * 처리 조건이 맞지 않아 처리되지 않은 상황이다.
         */
        when(subscriptionTimeoutTransactionService
                .processExpiredReservation(
                        any(UUID.class),
                        any(Instant.class)
                ))
                .thenReturn(false);

        when(subscriptionTimeoutTransactionService
                .processExpiredReservation(
                        eq(nextSubscriptionId),
                        any(Instant.class)
                ))
                .thenReturn(true);

        // when
        int result =
                subscriptionTimeoutService
                        .processExpiredReservations();

        // then
        assertThat(result).isEqualTo(1);

        verify(subscriptionRepository)
                .findExpiredProcessingSubscriptionTargetsAfter(
                        any(Instant.class),
                        eq(lastTarget.reservationExpiresAt()),
                        eq(lastTarget.subscriptionId()),
                        any(Pageable.class)
                );

        verify(subscriptionTimeoutTransactionService)
                .processExpiredReservation(
                        eq(nextSubscriptionId),
                        any(Instant.class)
                );
    }

    @Test
    @DisplayName(
            "한 번의 실행에서 설정된 최대 키셋 배치까지만 처리한다"
    )
    void stopsAfterConfiguredMaximumBatchCount() {
        // given
        timeoutProperties.setBatchSize(2);
        timeoutProperties.setMaxBatchesPerRun(2);

        Instant firstExpiresAt =
                Instant.parse("2026-09-01T00:00:00Z");

        Instant secondExpiresAt =
                Instant.parse("2026-09-02T00:00:00Z");

        ExpiredProcessingSubscriptionTarget first =
                new ExpiredProcessingSubscriptionTarget(
                        UUID.randomUUID(),
                        firstExpiresAt
                );

        ExpiredProcessingSubscriptionTarget second =
                new ExpiredProcessingSubscriptionTarget(
                        UUID.randomUUID(),
                        firstExpiresAt
                );

        List<ExpiredProcessingSubscriptionTarget> firstBatch =
                List.of(first, second)
                        .stream()
                        .sorted(
                                Comparator.comparing(
                                        ExpiredProcessingSubscriptionTarget
                                                ::subscriptionId
                                )
                        )
                        .toList();

        ExpiredProcessingSubscriptionTarget firstBatchLast =
                firstBatch.get(firstBatch.size() - 1);

        ExpiredProcessingSubscriptionTarget third =
                new ExpiredProcessingSubscriptionTarget(
                        UUID.randomUUID(),
                        secondExpiresAt
                );

        ExpiredProcessingSubscriptionTarget fourth =
                new ExpiredProcessingSubscriptionTarget(
                        UUID.randomUUID(),
                        secondExpiresAt
                );

        List<ExpiredProcessingSubscriptionTarget> secondBatch =
                List.of(third, fourth)
                        .stream()
                        .sorted(
                                Comparator.comparing(
                                        ExpiredProcessingSubscriptionTarget
                                                ::subscriptionId
                                )
                        )
                        .toList();

        ExpiredProcessingSubscriptionTarget secondBatchLast =
                secondBatch.get(secondBatch.size() - 1);

        when(subscriptionRepository
                .findExpiredProcessingSubscriptionTargets(
                        any(Instant.class),
                        any(Pageable.class)
                ))
                .thenReturn(firstBatch);

        when(subscriptionRepository
                .findExpiredProcessingSubscriptionTargetsAfter(
                        any(Instant.class),
                        eq(firstBatchLast.reservationExpiresAt()),
                        eq(firstBatchLast.subscriptionId()),
                        any(Pageable.class)
                ))
                .thenReturn(secondBatch);

        when(subscriptionTimeoutTransactionService
                .processExpiredReservation(
                        any(UUID.class),
                        any(Instant.class)
                ))
                .thenReturn(true);

        // when
        int result =
                subscriptionTimeoutService
                        .processExpiredReservations();

        // then
        assertThat(result).isEqualTo(4);

        /*
         * 두 번째 배치까지 모두 찼더라도 설정된 최대 배치 수가 2이므로
         * 세 번째 키셋 조회는 실행하지 않는다.
         */
        verify(subscriptionRepository, never())
                .findExpiredProcessingSubscriptionTargetsAfter(
                        any(Instant.class),
                        eq(secondBatchLast.reservationExpiresAt()),
                        eq(secondBatchLast.subscriptionId()),
                        any(Pageable.class)
                );
    }
}