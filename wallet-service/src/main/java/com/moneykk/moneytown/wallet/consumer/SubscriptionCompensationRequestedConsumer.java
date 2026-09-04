package com.moneykk.moneytown.wallet.consumer;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.wallet.consumer.dto.SubscriptionCompensationRequestedPayload;
import com.moneykk.moneytown.wallet.service.WalletHoldService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// 보상 트리거 이벤트 수신 → Hold 상태 보고 UNHOLD 또는 REFUND 처리
@Component
@RequiredArgsConstructor
public class SubscriptionCompensationRequestedConsumer {

    private final WalletHoldService walletHoldService;

    @KafkaListener(
            topics = "subscription-compensation-requested",
            groupId = "${spring.application.name}",
            containerFactory = "subscriptionCompensationRequestedKafkaListenerContainerFactory"
    )
    public void onSubscriptionCompensationRequested(EventEnvelope<SubscriptionCompensationRequestedPayload> event) {
        walletHoldService.compensateHold(event);
    }
}
