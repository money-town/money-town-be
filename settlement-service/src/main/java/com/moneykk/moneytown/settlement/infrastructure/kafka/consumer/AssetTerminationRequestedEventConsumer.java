package com.moneykk.moneytown.settlement.infrastructure.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.settlement.command.application.FinalSettlementCommandService;
import com.moneykk.moneytown.settlement.command.application.FinalSettlementDisbursementService;
import com.moneykk.moneytown.settlement.command.dto.FinalSettlementBatchResponse;
import com.moneykk.moneytown.settlement.command.dto.OpenFinalSettlementRequest;
import com.moneykk.moneytown.settlement.infrastructure.kafka.event.AssetTerminationRequestedPayload;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// 자산 종료 요청을 받아 최종 정산(원금반환)을 개시한다.
// 기존 동기 Feign(SettlementServiceClient.openFinalSettlement)이 하던 일과 동일 — 트리거만 이벤트로 바뀐다.
@Component
@RequiredArgsConstructor
public class AssetTerminationRequestedEventConsumer {

    private static final String EVENT_TYPE = "AssetTerminationRequested";
    private static final String SYSTEM_ROLE = "SYSTEM";

    private final ObjectMapper objectMapper;
    private final FinalSettlementCommandService finalSettlementCommandService;
    private final FinalSettlementDisbursementService finalSettlementDisbursementService;

    @KafkaListener(topics = "asset-termination-requested")
    public void consume(String message) throws JsonProcessingException {
        EventEnvelope<AssetTerminationRequestedPayload> event = readEvent(message);

        try {
            MDC.put("requestId", event.correlationId());

            if (!EVENT_TYPE.equals(event.eventType())) {
                throw new IllegalArgumentException("지원하지 않는 이벤트입니다: " + event.eventType());
            }

            FinalSettlementBatchResponse response =
                    finalSettlementCommandService.openFinalSettlement(SYSTEM_ROLE, toRequest(event.payload()));
            // newlyCreated 여부와 무관하게 항상 지급을 호출
            // openFinalSettlement가 배치 저장까지 끝낸 뒤 재기동/재전달로 이 메서드가 다시 불리면
            // newlyCreated=false로 기존 배치를 반환하는데, 그때 지급 호출을 건너뛰면 disburseAsync가
            // 한 번도 안 불린 채 메시지만 정상 처리돼 최대 3분지연이 생김
            // disburseAsync → claimPendingPayouts가 QUEUED/RETRYING만 원자적으로
            // 선점하므로 이미 처리된 배치에 또 호출해도 안전하다 — 게이트를 걸 이유가 없다
            finalSettlementDisbursementService.disburseAsync(response.finalSettlementBatchId());
        } finally {
            MDC.remove("requestId");
        }
    }

    private OpenFinalSettlementRequest toRequest(AssetTerminationRequestedPayload payload) {
        return new OpenFinalSettlementRequest(payload.assetId(), payload.terminatedAt(), payload.unitPrice());
    }

    private EventEnvelope<AssetTerminationRequestedPayload> readEvent(String message) throws JsonProcessingException {
        JavaType eventType = objectMapper.getTypeFactory()
                .constructParametricType(EventEnvelope.class, AssetTerminationRequestedPayload.class);
        return objectMapper.readValue(message, eventType);
    }
}