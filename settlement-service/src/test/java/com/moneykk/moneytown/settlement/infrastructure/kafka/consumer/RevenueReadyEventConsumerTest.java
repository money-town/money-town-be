package com.moneykk.moneytown.settlement.infrastructure.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.settlement.command.application.DividendDisbursementService;
import com.moneykk.moneytown.settlement.command.application.SettlementCommandService;
import com.moneykk.moneytown.settlement.command.dto.SettlementBatchResponse;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import com.moneykk.moneytown.settlement.infrastructure.client.RevenueTransferStatusNotifier;
import com.moneykk.moneytown.settlement.infrastructure.kafka.event.RevenueReadyPayload;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevenueReadyEventConsumerTest {

    @Mock
    private SettlementCommandService settlementCommandService;
    @Mock
    private RevenueTransferStatusNotifier revenueTransferStatusNotifier;
    @Mock
    private DividendDisbursementService dividendDisbursementService;

    private MeterRegistry meterRegistry;
    private ObjectMapper objectMapper;
    private RevenueReadyEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        meterRegistry = new SimpleMeterRegistry();
        consumer = new RevenueReadyEventConsumer(
                objectMapper, settlementCommandService, revenueTransferStatusNotifier,
                dividendDisbursementService, meterRegistry);
    }

    @Test
    @DisplayName("정상 처리 시 개시→통보→지급 3단계를 순서대로 호출한다")
    void opensBatchNotifiesAndDisburses() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID revenueId = UUID.randomUUID();
        SettlementBatchResponse response = batchResponse(assetId, revenueId, true);
        when(settlementCommandService.openBatchAutomatically(assetId, revenueId)).thenReturn(response);

        consumer.consume(message(assetId, revenueId));

        verify(settlementCommandService).openBatchAutomatically(assetId, revenueId);
        verify(revenueTransferStatusNotifier).notifyTransferredOrThrow(revenueId);
        verify(dividendDisbursementService).disburseAsync(response.settlementBatchId());
        assertThat(meterRegistry.counter("settlement.batch.auto_open", "result", "recovered_existing").count())
                .isZero();
    }

    @Test
    @DisplayName("기존 배치를 멱등 재사용해도(newlyCreated=false) 통보·지급은 그대로 호출하고 recovered_existing 메트릭을 남긴다")
    void stillNotifiesAndDisbursesWhenRecoveredExisting() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID revenueId = UUID.randomUUID();
        SettlementBatchResponse response = batchResponse(assetId, revenueId, false);
        when(settlementCommandService.openBatchAutomatically(assetId, revenueId)).thenReturn(response);

        consumer.consume(message(assetId, revenueId));

        verify(revenueTransferStatusNotifier).notifyTransferredOrThrow(revenueId);
        verify(dividendDisbursementService).disburseAsync(response.settlementBatchId());
        assertThat(meterRegistry.counter("settlement.batch.auto_open", "result", "recovered_existing").count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("SETTLEMENT_IN_PROGRESS_FOR_ASSET는 예외 없이 skip 처리하고, 통보·지급은 호출하지 않는다")
    void skipsWithoutErrorWhenAssetInProgress() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID revenueId = UUID.randomUUID();
        when(settlementCommandService.openBatchAutomatically(assetId, revenueId))
                .thenThrow(new BusinessException(SettlementErrorCode.SETTLEMENT_IN_PROGRESS_FOR_ASSET));

        consumer.consume(message(assetId, revenueId));

        verify(revenueTransferStatusNotifier, never()).notifyTransferredOrThrow(any());
        verify(dividendDisbursementService, never()).disburseAsync(any());
        assertThat(meterRegistry.counter("settlement.batch.auto_open", "result", "skipped_in_progress").count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("SETTLEMENT_IN_PROGRESS_FOR_ASSET가 아닌 다른 예외는 그대로 던져 Kafka 재시도 대상이 된다")
    void rethrowsUnexpectedBusinessException() {
        UUID assetId = UUID.randomUUID();
        UUID revenueId = UUID.randomUUID();
        when(settlementCommandService.openBatchAutomatically(assetId, revenueId))
                .thenThrow(new BusinessException(SettlementErrorCode.REVENUE_NOT_READY));

        assertThatThrownBy(() -> consumer.consume(message(assetId, revenueId)))
                .isInstanceOf(BusinessException.class);

        verify(revenueTransferStatusNotifier, never()).notifyTransferredOrThrow(any());
        verify(dividendDisbursementService, never()).disburseAsync(any());
    }

    @Test
    @DisplayName("수익 전달 통보(notifyTransferredOrThrow)가 실패하면 지급을 시작하지 않고 예외를 그대로 전파한다 (Kafka 재시도 대상)")
    void doesNotDisburseAndPropagatesWhenNotifyFails() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID revenueId = UUID.randomUUID();
        SettlementBatchResponse response = batchResponse(assetId, revenueId, true);
        when(settlementCommandService.openBatchAutomatically(assetId, revenueId)).thenReturn(response);
        org.mockito.Mockito.doThrow(new RuntimeException("asset-service 호출 실패"))
                .when(revenueTransferStatusNotifier).notifyTransferredOrThrow(revenueId);

        assertThatThrownBy(() -> consumer.consume(message(assetId, revenueId)))
                .isInstanceOf(RuntimeException.class);

        verify(dividendDisbursementService, never()).disburseAsync(any());
    }

    private String message(UUID assetId, UUID revenueId) throws Exception {
        EventEnvelope<RevenueReadyPayload> event = EventEnvelope.of(
                "RevenueReady", assetId.toString(), UUID.randomUUID(), "correlation-1",
                new RevenueReadyPayload(assetId, revenueId));
        return objectMapper.writeValueAsString(event);
    }

    private SettlementBatchResponse batchResponse(UUID assetId, UUID revenueId, boolean newlyCreated) {
        return new SettlementBatchResponse(UUID.randomUUID(), assetId, revenueId,
                LocalDate.of(2026, 9, 1), 1_000_000L, SettlementStatus.CALCULATED, 1, Instant.now(), newlyCreated);
    }
}