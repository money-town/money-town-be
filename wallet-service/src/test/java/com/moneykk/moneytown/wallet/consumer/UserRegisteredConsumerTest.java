package com.moneykk.moneytown.wallet.consumer;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.wallet.entity.Wallet;
import com.moneykk.moneytown.wallet.repository.WalletRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRegisteredConsumerTest {

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private UserRegisteredConsumer consumer;

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("UserRegistered면 지갑이 없을 때만 새로 생성한다")
    void onUserAccountEvent_userRegistered_createsWalletIfAbsent() {
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());

        consumer.onUserAccountEvent(userAccountEvent("UserRegistered"));

        verify(walletRepository).save(any(Wallet.class));
    }

    @Test
    @DisplayName("UserRegistered인데 지갑이 이미 있으면 다시 만들지 않는다 (멱등)")
    void onUserAccountEvent_userRegistered_alreadyExists_isIdempotent() {
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(new Wallet(userId)));

        consumer.onUserAccountEvent(userAccountEvent("UserRegistered"));

        verify(walletRepository, never()).save(any());
    }

    @Test
    @DisplayName("UserRegistered가 아닌 이벤트(예: UserWithdrawn)는 조용히 무시한다")
    void onUserAccountEvent_otherEventType_isIgnored() {
        consumer.onUserAccountEvent(userAccountEvent("UserWithdrawn"));

        verifyNoInteractions(walletRepository);
    }

    private EventEnvelope<Object> userAccountEvent(String eventType) {
        return EventEnvelope.of(eventType, userId.toString(), userId, "corr-1", new Object());
    }
}
