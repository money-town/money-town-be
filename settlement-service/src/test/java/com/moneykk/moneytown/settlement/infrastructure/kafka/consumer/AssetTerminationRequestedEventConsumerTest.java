package com.moneykk.moneytown.settlement.infrastructure.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.settlement.command.application.FinalSettlementCommandService;
import com.moneykk.moneytown.settlement.command.application.FinalSettlementDisbursementService;
import com.moneykk.moneytown.settlement.command.dto.FinalSettlementBatchResponse;
import com.moneykk.moneytown.settlement.command.dto.OpenFinalSettlementRequest;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import com.moneykk.moneytown.settlement.infrastructure.kafka.event.AssetTerminationRequestedPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetTerminationRequestedEventConsumerTest {

    @Mock
    private FinalSettlementCommandService finalSettlementCommandService;
    @Mock
    private FinalSettlementDisbursementService finalSettlementDisbursementService;

    private ObjectMapper objectMapper;
    private AssetTerminationRequestedEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        consumer = new AssetTerminationRequestedEventConsumer(
                objectMapper, finalSettlementCommandService, finalSettlementDisbursementService);
    }

    @Test
    @DisplayName("새로 생성된 배치면 지급을 호출한다")
    void disbursesWhenNewlyCreated() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        when(finalSettlementCommandService.openFinalSettlement(any(), any()))
                .thenReturn(batchResponse(assetId, batchId, true));

        consumer.consume(message(assetId));

        verify(finalSettlementDisbursementService).disburseAsync(batchId);
    }

    @Test
    @DisplayName("기존 최종 정산 배치를 반환해도(newlyCreated=false) 지급을 그대로 호출한다 — " +
            "openFinalSettlement 커밋 후 disburseAsync 호출 전 재기동/재전달되는 경우 지급이 누락되지 않아야 한다")
    void disbursesWhenExistingBatchIsRecovered() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID existingBatchId = UUID.randomUUID();
        when(finalSettlementCommandService.openFinalSettlement(any(), any()))
                .thenReturn(batchResponse(assetId, existingBatchId, false));

        consumer.consume(message(assetId));

        verify(finalSettlementDisbursementService).disburseAsync(existingBatchId);
    }

    @Test
    @DisplayName("지원하지 않는 이벤트 타입이면 예외를 던지고 지급을 호출하지 않는다")
    void rejectsUnsupportedEventType() {
        UUID assetId = UUID.randomUUID();
        EventEnvelope<AssetTerminationRequestedPayload> event = EventEnvelope.of(
                "UnknownEvent", assetId.toString(), UUID.randomUUID(), "correlation-1",
                new AssetTerminationRequestedPayload(assetId, Instant.now(), 10_000L));

        assertThatThrownBy(() -> consumer.consume(objectMapper.writeValueAsString(event)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(finalSettlementDisbursementService, org.mockito.Mockito.never()).disburseAsync(any());
    }

    private String message(UUID assetId) throws Exception {
        EventEnvelope<AssetTerminationRequestedPayload> event = EventEnvelope.of(
                "AssetTerminationRequested", assetId.toString(), UUID.randomUUID(), "correlation-1",
                new AssetTerminationRequestedPayload(assetId, Instant.now(), 10_000L));
        return objectMapper.writeValueAsString(event);
    }

    private FinalSettlementBatchResponse batchResponse(UUID assetId, UUID batchId, boolean newlyCreated) {
        return new FinalSettlementBatchResponse(batchId, assetId, 1_000_000L, SettlementStatus.CALCULATED, newlyCreated);
    }

    // toRequest 매핑 검증 — payload 필드가 OpenFinalSettlementRequest로 정확히 옮겨지는지
    @Test
    @DisplayName("payload를 OpenFinalSettlementRequest로 정확히 매핑해 호출한다")
    void mapsPayloadToRequest() throws Exception {
        UUID assetId = UUID.randomUUID();
        Instant terminatedAt = Instant.parse("2026-09-14T04:00:00Z");
        EventEnvelope<AssetTerminationRequestedPayload> event = EventEnvelope.of(
                "AssetTerminationRequested", assetId.toString(), UUID.randomUUID(), "correlation-1",
                new AssetTerminationRequestedPayload(assetId, terminatedAt, 12_345L));
        when(finalSettlementCommandService.openFinalSettlement(any(), any()))
                .thenReturn(batchResponse(assetId, UUID.randomUUID(), true));

        consumer.consume(objectMapper.writeValueAsString(event));

        org.mockito.ArgumentCaptor<OpenFinalSettlementRequest> captor =
                org.mockito.ArgumentCaptor.forClass(OpenFinalSettlementRequest.class);
        verify(finalSettlementCommandService).openFinalSettlement(org.mockito.ArgumentMatchers.eq("SYSTEM"), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().assetId()).isEqualTo(assetId);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().terminatedAt()).isEqualTo(terminatedAt);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().unitPrice()).isEqualTo(12_345L);
    }
}