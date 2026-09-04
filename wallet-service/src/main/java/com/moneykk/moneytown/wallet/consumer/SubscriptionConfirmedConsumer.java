package com.moneykk.moneytown.wallet.consumer;

import com.moneykk.moneytown.wallet.consumer.dto.SubscriptionConfirmedEvent;
import com.moneykk.moneytown.wallet.service.WalletHoldService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
// 청약 확정 이벤트 수신 → 동결 확정 및 최종 차감(DEDUCT) 처리

@Component
@RequiredArgsConstructor
public class SubscriptionConfirmedConsumer {

    private final WalletHoldService walletHoldService;

    @KafkaListener(
            topics = "subscription-confirmed",
            groupId = "${spring.application.name}",
            containerFactory = "subscriptionConfirmedKafkaListenerContainerFactory"
    )
    public void onSubscriptionConfirmed(SubscriptionConfirmedEvent event) {
        walletHoldService.confirmHold(event);
    }
}
