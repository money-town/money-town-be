package com.moneykk.moneytown.settlement.infrastructure.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.settlement.command.application.DividendDisbursementService;
import com.moneykk.moneytown.settlement.infrastructure.kafka.event.WalletDividendResultPayload;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// 지갑이 배당 입금 처리 결과를 알려오면(Wallet → Settlement) payout 상태에 반영
@Component
@RequiredArgsConstructor
public class DividendPayoutResultConsumer {

    private final ObjectMapper objectMapper;
    private final DividendDisbursementService dividendDisbursementService;

    @KafkaListener(
            topics = WalletDividendResultPayload.TOPIC,
            concurrency = "${SETTLEMENT_DIVIDEND_RESULT_CONSUMER_CONCURRENCY:4}")
    public void consume(String message) throws JsonProcessingException {
        EventEnvelope<WalletDividendResultPayload> event = readEvent(message);

        try {
            MDC.put("requestId", event.correlationId());

            boolean succeeded = WalletDividendResultPayload.SUCCEEDED_EVENT_TYPE.equals(event.eventType());
            if (!succeeded && !WalletDividendResultPayload.FAILED_EVENT_TYPE.equals(event.eventType())) {
                throw new IllegalArgumentException("지원하지 않는 이벤트입니다: " + event.eventType());
            }

            WalletDividendResultPayload payload = event.payload();
            dividendDisbursementService.applyDispatchResult(
                    payload.payoutId(), payload.settlementBatchId(), succeeded, payload.reason());
        } finally {
            MDC.remove("requestId");
        }
    }

    private EventEnvelope<WalletDividendResultPayload> readEvent(String message) throws JsonProcessingException {
        JavaType eventType = objectMapper.getTypeFactory()
                .constructParametricType(EventEnvelope.class, WalletDividendResultPayload.class);
        return objectMapper.readValue(message, eventType);
    }
}