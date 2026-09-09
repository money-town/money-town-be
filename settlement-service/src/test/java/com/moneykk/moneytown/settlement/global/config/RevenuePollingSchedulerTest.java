package com.moneykk.moneytown.settlement.global.config;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.settlement.command.application.DividendDisbursementService;
import com.moneykk.moneytown.settlement.command.application.SettlementCommandService;
import com.moneykk.moneytown.settlement.command.dto.SettlementBatchResponse;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import com.moneykk.moneytown.settlement.infrastructure.client.AssetServiceClient;
import com.moneykk.moneytown.settlement.infrastructure.client.RevenueTransferStatusNotifier;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.ReadyRevenueListResponse;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.RevenueResponse;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.RevenueTransferStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevenuePollingSchedulerTest {

    @Mock
    private AssetServiceClient assetServiceClient;
    @Mock
    private SettlementCommandService settlementCommandService;
    @Mock
    private RevenueTransferStatusNotifier revenueTransferStatusNotifier;
    @Mock
    private DividendDisbursementService dividendDisbursementService;

    @InjectMocks
    private RevenuePollingScheduler revenuePollingScheduler;

    @Test
    @DisplayName("단일 페이지에 담긴 READY 수익 각각에 대해 정산 회차 자동 개시를 시도한다")
    void opensBatchForEachReadyRevenueInSinglePage() {
        RevenueResponse revenue1 = revenue(UUID.randomUUID(), UUID.randomUUID());
        RevenueResponse revenue2 = revenue(UUID.randomUUID(), UUID.randomUUID());
        SettlementBatchResponse response1 = batchResponse(revenue1);
        SettlementBatchResponse response2 = batchResponse(revenue2);
        when(assetServiceClient.getReadyRevenues("SYSTEM", null))
                .thenReturn(ApiResponse.success(page(List.of(revenue1, revenue2), null, false), null));
        when(settlementCommandService.openBatchAutomatically(revenue1.assetId(), revenue1.revenueId()))
                .thenReturn(response1);
        when(settlementCommandService.openBatchAutomatically(revenue2.assetId(), revenue2.revenueId()))
                .thenReturn(response2);

        revenuePollingScheduler.pollReadyRevenues();

        verify(settlementCommandService).openBatchAutomatically(revenue1.assetId(), revenue1.revenueId());
        verify(settlementCommandService).openBatchAutomatically(revenue2.assetId(), revenue2.revenueId());
        verify(revenueTransferStatusNotifier).notifyTransferred(revenue1.revenueId());
        verify(revenueTransferStatusNotifier).notifyTransferred(revenue2.revenueId());
        verify(dividendDisbursementService).disburseAsync(response1.settlementBatchId());
        verify(dividendDisbursementService).disburseAsync(response2.settlementBatchId());
    }

    @Test
    @DisplayName("여러 페이지로 나뉘어 오면 cursor를 따라가며 모든 페이지를 처리한다")
    void followsCursorAcrossMultiplePages() {
        RevenueResponse revenue1 = revenue(UUID.randomUUID(), UUID.randomUUID());
        RevenueResponse revenue2 = revenue(UUID.randomUUID(), UUID.randomUUID());
        UUID cursor = revenue1.revenueId();
        when(assetServiceClient.getReadyRevenues("SYSTEM", null))
                .thenReturn(ApiResponse.success(page(List.of(revenue1), cursor, true), null));
        when(assetServiceClient.getReadyRevenues("SYSTEM", cursor))
                .thenReturn(ApiResponse.success(page(List.of(revenue2), null, false), null));
        when(settlementCommandService.openBatchAutomatically(revenue1.assetId(), revenue1.revenueId()))
                .thenReturn(batchResponse(revenue1));
        when(settlementCommandService.openBatchAutomatically(revenue2.assetId(), revenue2.revenueId()))
                .thenReturn(batchResponse(revenue2));

        revenuePollingScheduler.pollReadyRevenues();

        verify(settlementCommandService).openBatchAutomatically(revenue1.assetId(), revenue1.revenueId());
        verify(settlementCommandService).openBatchAutomatically(revenue2.assetId(), revenue2.revenueId());
    }

    @Test
    @DisplayName("특정 수익 처리 중 비즈니스 예외가 발생해도 다음 수익 처리를 계속한다")
    void continuesToNextRevenueWhenOneFails() {
        RevenueResponse failing = revenue(UUID.randomUUID(), UUID.randomUUID());
        RevenueResponse succeeding = revenue(UUID.randomUUID(), UUID.randomUUID());
        when(assetServiceClient.getReadyRevenues("SYSTEM", null))
                .thenReturn(ApiResponse.success(page(List.of(failing, succeeding), null, false), null));
        when(settlementCommandService.openBatchAutomatically(failing.assetId(), failing.revenueId()))
                .thenThrow(new BusinessException(SettlementErrorCode.SETTLEMENT_IN_PROGRESS_FOR_ASSET));
        when(settlementCommandService.openBatchAutomatically(succeeding.assetId(), succeeding.revenueId()))
                .thenReturn(batchResponse(succeeding));

        revenuePollingScheduler.pollReadyRevenues();

        verify(settlementCommandService).openBatchAutomatically(succeeding.assetId(), succeeding.revenueId());
        verify(revenueTransferStatusNotifier).notifyTransferred(succeeding.revenueId());
        verify(revenueTransferStatusNotifier, never()).notifyTransferred(failing.revenueId());
    }

    @Test
    @DisplayName("대기 중인 수익이 없으면 아무 것도 시도하지 않는다")
    void doesNothingWhenNoReadyRevenues() {
        when(assetServiceClient.getReadyRevenues("SYSTEM", null))
                .thenReturn(ApiResponse.success(page(List.of(), null, false), null));

        revenuePollingScheduler.pollReadyRevenues();

        verify(settlementCommandService, never()).openBatchAutomatically(any(), any());
    }

    private RevenueResponse revenue(UUID assetId, UUID revenueId) {
        return new RevenueResponse(revenueId, assetId, "RENT", "PROPERTY_MANAGER", "REF-1",
                BigDecimal.valueOf(1_000_000), BigDecimal.ZERO, BigDecimal.ZERO,
                "KRW", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1), RevenueTransferStatus.READY);
    }

    private ReadyRevenueListResponse page(List<RevenueResponse> revenues, UUID nextCursor, boolean hasNext) {
        return new ReadyRevenueListResponse(revenues, nextCursor, hasNext);
    }

    private SettlementBatchResponse batchResponse(RevenueResponse revenue) {
        return new SettlementBatchResponse(UUID.randomUUID(), revenue.assetId(), revenue.revenueId(),
                LocalDate.of(2026, 9, 1), 1_000_000L, 0L, 0L, SettlementStatus.CALCULATED, 1, Instant.now());
    }
}
