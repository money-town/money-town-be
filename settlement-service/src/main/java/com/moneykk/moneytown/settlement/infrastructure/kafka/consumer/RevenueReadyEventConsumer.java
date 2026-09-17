package com.moneykk.moneytown.settlement.infrastructure.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.settlement.command.application.DividendDisbursementService;
import com.moneykk.moneytown.settlement.command.application.SettlementCommandService;
import com.moneykk.moneytown.settlement.command.dto.SettlementBatchResponse;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import com.moneykk.moneytown.settlement.infrastructure.client.RevenueTransferStatusNotifier;
import com.moneykk.moneytown.settlement.infrastructure.kafka.event.RevenueReadyPayload;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// 수익 준비(RevenueReady) 이벤트를 받아 정산 회차를 자동 개시한다.
// RevenuePollingScheduler.tryOpenBatch가 하던 일과 동일 — 트리거만 이벤트로 바뀐다.
// 폴링은 삭제하지 않고 정합성 백스톱(주기 완화)으로 남는다
@Slf4j
@Component
@RequiredArgsConstructor
public class RevenueReadyEventConsumer {

    private static final String EVENT_TYPE = "RevenueReady";

    private final ObjectMapper objectMapper;
    private final SettlementCommandService settlementCommandService;
    private final RevenueTransferStatusNotifier revenueTransferStatusNotifier;
    private final DividendDisbursementService dividendDisbursementService;
    private final MeterRegistry meterRegistry;

    @KafkaListener(topics = "revenue-ready")
    public void consume(String message) throws JsonProcessingException {
        EventEnvelope<RevenueReadyPayload> event = readEvent(message);

        try {
            MDC.put("requestId", event.correlationId());

            if (!EVENT_TYPE.equals(event.eventType())) {
                throw new IllegalArgumentException("지원하지 않는 이벤트입니다: " + event.eventType());
            }

            RevenueReadyPayload payload = event.payload();
            try {
                SettlementBatchResponse response =
                        settlementCommandService.openBatchAutomatically(payload.assetId(), payload.revenueId());
                // newlyCreated 여부와 무관하게 항상 3단계를 전부 호출한다
                // notifyTransferred를 건너뛰면 revenue가 TRANSFERRED로 못 넘어가는 걸 복구할 스케줄러 X
                if (!response.newlyCreated()) {
                    meterRegistry.counter("settlement.batch.auto_open", "result", "recovered_existing").increment();
                }
                // notifyTransferredOrThrow(실패를 삼키지 않음) —
                // 실패하면 이 메시지는 여기서 예외로 끝나 Kafka 재시도(DLT 전 3회) 대상이 되고,
                // 지급(disburseAsync)도 통보가 성공한 뒤에만 시작된다.
                revenueTransferStatusNotifier.notifyTransferredOrThrow(response.revenueId());
                dividendDisbursementService.disburseAsync(response.settlementBatchId());
            } catch (BusinessException e) {
                if (e.getErrorCode() != SettlementErrorCode.SETTLEMENT_IN_PROGRESS_FOR_ASSET) {
                    // 여기서 넓게 catch하면 진짜 장애까지 조용히 삼켜 소실될 위험이 있다
                    throw e;
                }
                // 자산당 진행 중 배치 1개 제약 — 같은 자산의 다른 revenue가 먼저 배치를 잡은 정상 상황.
                // 에러가 아니라 성공(ack) 처리하고 3분(추후 30분) 폴링 백스톱이 재수거하도록 넘긴다.
                meterRegistry.counter("settlement.batch.auto_open", "result", "skipped_in_progress").increment();
                log.debug("정산 회차 자동 개시 건너뜀 (assetId={}, revenueId={}, reason={})",
                        payload.assetId(), payload.revenueId(), e.getErrorCode());
            }
        } finally {
            MDC.remove("requestId");
        }
    }

    private EventEnvelope<RevenueReadyPayload> readEvent(String message) throws JsonProcessingException {
        JavaType eventType = objectMapper.getTypeFactory()
                .constructParametricType(EventEnvelope.class, RevenueReadyPayload.class);
        return objectMapper.readValue(message, eventType);
    }
}