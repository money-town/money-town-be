package com.moneykk.moneytown.wallet.consumer;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.wallet.consumer.dto.SubscriptionReservedPayload;
import com.moneykk.moneytown.wallet.service.WalletHoldService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// 청약 예약 이벤트 수신 → 청약금 동결(HOLD) 처리
@Component
@RequiredArgsConstructor
public class SubscriptionReservedConsumer {

    private final WalletHoldService walletHoldService;

    @KafkaListener(
            topics = "subscription-reserved",
            groupId = "${spring.application.name}",
            containerFactory = "subscriptionReservedKafkaListenerContainerFactory"
    )
    public void onSubscriptionReserved(EventEnvelope<SubscriptionReservedPayload> event) {
        walletHoldService.processReservation(event);
    }
}
