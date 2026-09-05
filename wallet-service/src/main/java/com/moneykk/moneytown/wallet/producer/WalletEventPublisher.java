package com.moneykk.moneytown.wallet.producer;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.wallet.producer.dto.WalletCompensationResultPayload;
import com.moneykk.moneytown.wallet.producer.dto.WalletHoldResultPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

// WalletHoldService가 만든 결과 이벤트를 실제 Kafka로 발행하는 곳 (Wallet → Offering)
@Component
@RequiredArgsConstructor
public class WalletEventPublisher {

    private static final String WALLET_HOLD_RESULT_TOPIC = "wallet-hold-result";
    private static final String WALLET_COMPENSATION_RESULT_TOPIC = "wallet-compensation-result";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // EventEnvelope<T>는 제네릭이라 타입 소거 때문에 publish(EventEnvelope) 오버로드로 묶을 수 없어 메서드명을 분리한다.
    public void publishHoldResult(EventEnvelope<WalletHoldResultPayload> event) {
        kafkaTemplate.send(WALLET_HOLD_RESULT_TOPIC, event.userId().toString(), event);
    }

    public void publishCompensationResult(EventEnvelope<WalletCompensationResultPayload> event) {
        kafkaTemplate.send(WALLET_COMPENSATION_RESULT_TOPIC, event.userId().toString(), event);
    }
}
