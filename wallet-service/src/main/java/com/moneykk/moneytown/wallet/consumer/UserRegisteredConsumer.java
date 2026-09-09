package com.moneykk.moneytown.wallet.consumer;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.wallet.entity.Wallet;
import com.moneykk.moneytown.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

// 같은 토픽에 다른 이벤트도 올 수 있어 eventType으로 걸러 UserRegistered만 처리한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredConsumer {

    private static final String EVENT_TYPE_USER_REGISTERED = "UserRegistered";

    private final WalletRepository walletRepository;

    @KafkaListener(
            topics = "user.account-events.v1",
            groupId = "${spring.application.name}",
            containerFactory = "userAccountEventKafkaListenerContainerFactory"
    )
    public void onUserAccountEvent(EventEnvelope<Object> event) {
        if (!EVENT_TYPE_USER_REGISTERED.equals(event.eventType())) {
            return;
        }

        UUID userId = event.userId();
        if (walletRepository.findByUserId(userId).isPresent()) {
            log.info("이미 지갑이 존재하여 스킵합니다. userId={}", userId);
            return;
        }

        walletRepository.save(new Wallet(userId));
        log.info("지갑을 자동생성했습니다. userId={}", userId);
    }
}
