package com.moneykk.moneytown.wallet.consumer;

import com.moneykk.moneytown.wallet.consumer.dto.UserRegisteredEvent;
import com.moneykk.moneytown.wallet.entity.Wallet;
import com.moneykk.moneytown.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// UserRegistered 컨슈밍 시 지갑 자동생성.
// Kafka는 같은 메시지를 두 번 보낼 수 있기때문에 이미 지갑이 있으면 다시 만들지 않는다.
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredConsumer {

    private final WalletRepository walletRepository;

    @KafkaListener(
            topics = "user.account-events.v1",
            groupId = "${spring.application.name}",
            containerFactory = "userRegisteredKafkaListenerContainerFactory"
    )
    public void onUserRegistered(UserRegisteredEvent event) {
        if (walletRepository.findByUserId(event.userId()).isPresent()) {
            log.info("이미 지갑이 존재하여 스킵합니다. userId={}", event.userId());
            return;
        }

        walletRepository.save(new Wallet(event.userId()));
        log.info("지갑을 자동생성했습니다. userId={}", event.userId());
    }
}
