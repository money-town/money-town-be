package com.moneykk.moneytown.wallet.producer;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.wallet.producer.dto.WalletCompensationResultPayload;
import com.moneykk.moneytown.wallet.producer.dto.WalletHoldResultPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

// WalletHoldService가 만든 결과 이벤트를 실제 Kafka로 발행하는 곳 (Wallet → Offering)
// Outbox 미구현 상태라 DB 커밋과 발행이 원자적이지 않음 — 발행 실패가 유실되지 않게 최소한 로그는 남긴다.
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletEventPublisher {

    private static final String WALLET_HOLD_RESULT_TOPIC = "wallet-hold-result";
    private static final String WALLET_COMPENSATION_RESULT_TOPIC = "wallet-compensation-result";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // EventEnvelope<T>는 제네릭이라 타입 소거 때문에 publish(EventEnvelope) 오버로드로 묶을 수 없어 메서드명을 분리한다.
    public void publishHoldResult(EventEnvelope<WalletHoldResultPayload> event) {
        send(WALLET_HOLD_RESULT_TOPIC, event.userId().toString(), event, event.aggregateId());
    }

    public void publishCompensationResult(EventEnvelope<WalletCompensationResultPayload> event) {
        send(WALLET_COMPENSATION_RESULT_TOPIC, event.userId().toString(), event, event.aggregateId());
    }

    // KafkaTemplate.send()는 비동기 API지만, producer 버퍼가 꽉 차는 등의 이유로 즉시 KafkaException을 던질 수도 있다.
    // 그 경우 whenComplete가 등록조차 안 돼서 실패 로그가 안 남으므로 try/catch로 한 번 더 감싼다.
    private void send(String topic, String key, Object event, String aggregateId) {
        try {
            kafkaTemplate.send(topic, key, event)
                    .whenComplete((result, ex) -> logIfFailed(topic, aggregateId, ex));
        } catch (KafkaException e) {
            logIfFailed(topic, aggregateId, e);
            throw e;
        }
    }

    private void logIfFailed(String topic, String aggregateId, Throwable ex) {
        if (ex != null) {
            log.error("이벤트 발행 실패: topic={}, aggregateId={}", topic, aggregateId, ex);
        }
    }
}
