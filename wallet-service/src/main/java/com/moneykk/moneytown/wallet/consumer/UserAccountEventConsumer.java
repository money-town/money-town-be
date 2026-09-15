package com.moneykk.moneytown.wallet.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.wallet.consumer.dto.UserWithdrawnPayload;
import com.moneykk.moneytown.wallet.entity.Wallet;
import com.moneykk.moneytown.wallet.repository.WalletRepository;
import com.moneykk.moneytown.wallet.service.WalletWithdrawalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

// 같은 토픽에 여러 이벤트가 섞여 오므로 eventType으로 분기해서 처리한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class UserAccountEventConsumer {

    private static final String EVENT_TYPE_USER_REGISTERED = "UserRegistered";
    private static final String EVENT_TYPE_USER_WITHDRAWN = "UserWithdrawn";

    private final WalletRepository walletRepository;
    private final WalletWithdrawalService walletWithdrawalService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "user.account-events.v1",
            groupId = "${spring.application.name}",
            containerFactory = "userAccountEventKafkaListenerContainerFactory"
    )
    public void onUserAccountEvent(EventEnvelope<Object> event) {
        try {
            MDC.put("requestId", event.correlationId());
            String eventType = event.eventType();
            switch (eventType == null ? "" : eventType) {
                case EVENT_TYPE_USER_REGISTERED -> handleUserRegistered(event.userId());
                case EVENT_TYPE_USER_WITHDRAWN -> handleUserWithdrawn(event);
                default -> log.debug("처리하지 않는 이벤트 타입 무시: eventType={}", eventType);
            }
        } finally {
            MDC.remove("requestId");
        }
    }

    private void handleUserRegistered(UUID userId) {
        if (walletRepository.findByUserId(userId).isPresent()) {
            log.info("이미 지갑이 존재하여 스킵합니다. userId={}", userId);
            return;
        }

        walletRepository.save(new Wallet(userId));
        log.info("지갑을 자동생성했습니다. userId={}", userId);
    }

    private void handleUserWithdrawn(EventEnvelope<Object> event) {
        UserWithdrawnPayload payload = objectMapper.convertValue(event.payload(), UserWithdrawnPayload.class);
        walletWithdrawalService.handleUserWithdrawn(event.userId(), payload.withdrawnBy());
    }
}
