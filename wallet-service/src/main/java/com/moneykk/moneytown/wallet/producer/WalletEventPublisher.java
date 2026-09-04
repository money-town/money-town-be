package com.moneykk.moneytown.wallet.producer;

import com.moneykk.moneytown.wallet.producer.dto.WalletCompensationResultEvent;
import com.moneykk.moneytown.wallet.producer.dto.WalletHoldResultEvent;
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

    public void publish(WalletHoldResultEvent event) {
        kafkaTemplate.send(WALLET_HOLD_RESULT_TOPIC, event.userId().toString(), event);
    }

    public void publish(WalletCompensationResultEvent event) {
        kafkaTemplate.send(WALLET_COMPENSATION_RESULT_TOPIC, event.userId().toString(), event);
    }
}
