package com.moneykk.moneytown.settlement.infrastructure.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.settlement.command.application.DividendDisbursementService;
import com.moneykk.moneytown.settlement.infrastructure.kafka.event.DividendPayoutDispatchPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// 배당 지급 요청(DividendPayoutDispatchRequested)을 받아 payout 1건씩 지갑에 입금한다
// 동시성은 환경변수로 받는다(기본 1). 파티션 수(8)보다 크게 잡아도 초과분은 유휴다.
// attempt()는 내부에서 모든 예외를 markFailedAttempt로 바꾸므로 여기까지 올라오는 예외는
// 역직렬화 실패·처리기 버그 같은 시스템 오류뿐이고, 그런 경우만 Kafka 재시도 후 DLT로 간다.
@Component
@RequiredArgsConstructor
public class DividendPayoutDispatchEventConsumer {

    private final ObjectMapper objectMapper;
    private final DividendDisbursementService dividendDisbursementService;

    @KafkaListener(
            topics = DividendPayoutDispatchPayload.TOPIC,
            concurrency = "${SETTLEMENT_DISPATCH_CONSUMER_CONCURRENCY:1}")
    public void consume(String message) throws JsonProcessingException {
        EventEnvelope<DividendPayoutDispatchPayload> event = readEvent(message);

        if (!DividendPayoutDispatchPayload.EVENT_TYPE.equals(event.eventType())) {
            throw new IllegalArgumentException("지원하지 않는 이벤트입니다: " + event.eventType());
        }

        DividendPayoutDispatchPayload payload = event.payload();
        dividendDisbursementService.processDispatchedPayout(
                payload.payoutId(), payload.settlementBatchId(), payload.investorId(), payload.amount());
    }

    private EventEnvelope<DividendPayoutDispatchPayload> readEvent(String message) throws JsonProcessingException {
        JavaType eventType = objectMapper.getTypeFactory()
                .constructParametricType(EventEnvelope.class, DividendPayoutDispatchPayload.class);
        return objectMapper.readValue(message, eventType);
    }
}