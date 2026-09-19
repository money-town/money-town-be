package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.offering.offering.command.scheduler.OfferingSchedulerMetrics;
import com.moneykk.moneytown.offering.subscription.command.config.SubscriptionTimeoutProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionTimeoutServiceTest {

    @Mock
    private OfferingSchedulerMetrics offeringSchedulerMetrics;
    @Mock
    private SubscriptionTimeoutBatchTransactionService batchService;
    @Mock
    private SubscriptionTimeoutTransactionService itemService;

    private SubscriptionTimeoutProperties properties;
    private SubscriptionTimeoutService service;

    @BeforeEach
    void setUp() {
        properties = new SubscriptionTimeoutProperties();
        properties.setBatchSize(100);
        properties.setMaxBatchesPerRun(2);

        service = new SubscriptionTimeoutService(
                offeringSchedulerMetrics,
                batchService,
                itemService,
                properties
        );
    }

    @Test
    @DisplayName("예약 만료 대상이 없으면 첫 배치 조회 후 종료한다")
    void stopsWhenNoExpiredBatchExists() {
        when(batchService.processNextBatch(any(Instant.class), eq(100)))
                .thenReturn(0);

        int result = service.processExpiredReservations();

        assertThat(result).isZero();
        verify(batchService).processNextBatch(any(Instant.class), eq(100));
        verifyNoInteractions(itemService);
    }

    @Test
    @DisplayName("공모 단위 배치 처리 건수를 합산한다")
    void sumsProcessedBatchCounts() {
        when(batchService.processNextBatch(any(Instant.class), eq(100)))
                .thenReturn(100, 50);

        int result = service.processExpiredReservations();

        assertThat(result).isEqualTo(150);
        verify(batchService, times(2))
                .processNextBatch(any(Instant.class), eq(100));
    }

    @Test
    @DisplayName("한 실행에서 설정된 최대 배치 수까지만 처리한다")
    void stopsAfterConfiguredMaximumBatchCount() {
        properties.setBatchSize(20);
        properties.setMaxBatchesPerRun(3);

        when(batchService.processNextBatch(any(Instant.class), eq(20)))
                .thenReturn(20);

        int result = service.processExpiredReservations();

        assertThat(result).isEqualTo(60);
        verify(batchService, times(3))
                .processNextBatch(any(Instant.class), eq(20));
    }

    @Test
    @DisplayName("배치 실패 시 포함된 청약을 건별 독립 트랜잭션으로 재처리한다")
    void retriesFailedBatchIndividually() {
        UUID offeringId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        when(batchService.processNextBatch(any(Instant.class), eq(100)))
                .thenThrow(new SubscriptionTimeoutBatchException(
                        offeringId,
                        List.of(firstId, secondId),
                        new IllegalStateException("배치 처리 실패")
                ))
                .thenReturn(0);

        when(itemService.processExpiredReservation(
                eq(firstId), any(Instant.class)))
                .thenReturn(true);
        when(itemService.processExpiredReservation(
                eq(secondId), any(Instant.class)))
                .thenReturn(false);

        int result = service.processExpiredReservations();

        assertThat(result).isEqualTo(1);
        verify(itemService).processExpiredReservation(
                eq(firstId), any(Instant.class));
        verify(itemService).processExpiredReservation(
                eq(secondId), any(Instant.class));
    }

    @Test
    @DisplayName("건별 복구 한 건이 실패해도 같은 배치의 다음 청약을 처리한다")
    void continuesAfterIndividualRecoveryFailure() {
        UUID offeringId = UUID.randomUUID();
        UUID failedId = UUID.randomUUID();
        UUID nextId = UUID.randomUUID();

        when(batchService.processNextBatch(any(Instant.class), eq(100)))
                .thenThrow(new SubscriptionTimeoutBatchException(
                        offeringId,
                        List.of(failedId, nextId),
                        new IllegalStateException("배치 처리 실패")
                ))
                .thenReturn(0);

        when(itemService.processExpiredReservation(
                eq(failedId), any(Instant.class)))
                .thenThrow(new IllegalStateException("건별 처리 실패"));
        when(itemService.processExpiredReservation(
                eq(nextId), any(Instant.class)))
                .thenReturn(true);

        int result = service.processExpiredReservations();

        assertThat(result).isEqualTo(1);
        verify(offeringSchedulerMetrics)
                .recordSubscriptionTimeoutItemFailure();
        verify(itemService).processExpiredReservation(
                eq(nextId), any(Instant.class));
    }

    @Test
    @DisplayName("대상 선점 단계의 시스템 장애는 현재 실행을 중단한다")
    void stopsAfterFailureWithoutClaimedSubscriptionIds() {
        when(batchService.processNextBatch(any(Instant.class), eq(100)))
                .thenThrow(new IllegalStateException("DB 조회 실패"));

        int result = service.processExpiredReservations();

        assertThat(result).isZero();
        verify(offeringSchedulerMetrics)
                .recordSubscriptionTimeoutBatchFailure();
        verify(batchService)
                .processNextBatch(any(Instant.class), eq(100));
        verifyNoInteractions(itemService);
    }
}
