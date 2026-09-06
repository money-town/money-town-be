package com.moneykk.moneytown.wallet.consumer;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.wallet.service.WalletHoldService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// 청약 확정 이벤트 수신 → 동결 확정 및 최종 차감(DEDUCT) 처리
@Component
@RequiredArgsConstructor
public class SubscriptionConfirmedConsumer {

    private final WalletHoldService walletHoldService;

    // payload에는 Holding용 필드도 오지만 Wallet은 aggregateId만으로 Hold를 찾으므로 사용하지 않는다.
    @KafkaListener(
            topics = "subscription-confirmed",
            groupId = "${spring.application.name}",
            containerFactory = "subscriptionConfirmedKafkaListenerContainerFactory"
    )
    public void onSubscriptionConfirmed(EventEnvelope<Object> event) {
        walletHoldService.confirmHold(event);
    }
}
