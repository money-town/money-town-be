package com.moneykk.moneytown.wallet.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.wallet.entity.Wallet;
import com.moneykk.moneytown.wallet.repository.WalletRepository;
import com.moneykk.moneytown.wallet.service.WalletWithdrawalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountEventConsumerTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletWithdrawalService walletWithdrawalService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private UserAccountEventConsumer consumer;

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("UserRegistered면 지갑이 없을 때만 새로 생성한다")
    void onUserAccountEvent_userRegistered_createsWalletIfAbsent() {
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());

        consumer.onUserAccountEvent(userAccountEvent("UserRegistered", Map.of()));

        verify(walletRepository).save(any(Wallet.class));
    }

    @Test
    @DisplayName("UserRegistered인데 지갑이 이미 있으면 다시 만들지 않는다 (멱등)")
    void onUserAccountEvent_userRegistered_alreadyExists_isIdempotent() {
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(new Wallet(userId)));

        consumer.onUserAccountEvent(userAccountEvent("UserRegistered", Map.of()));

        verify(walletRepository, never()).save(any());
    }

    @Test
    @DisplayName("UserWithdrawn이면 payload의 withdrawnBy를 꺼내 탈퇴 처리 서비스에 위임한다")
    void onUserAccountEvent_userWithdrawn_delegatesToWithdrawalService() {
        UUID withdrawnBy = UUID.randomUUID();

        consumer.onUserAccountEvent(userAccountEvent("UserWithdrawn", Map.of("withdrawnBy", withdrawnBy.toString())));

        verify(walletWithdrawalService).handleUserWithdrawn(userId, withdrawnBy);
    }

    @Test
    @DisplayName("UserRegistered/UserWithdrawn이 아닌 이벤트는 조용히 무시한다")
    void onUserAccountEvent_otherEventType_isIgnored() {
        consumer.onUserAccountEvent(userAccountEvent("SomeOtherEvent", Map.of()));

        verifyNoInteractions(walletRepository, walletWithdrawalService);
    }

    private EventEnvelope<Object> userAccountEvent(String eventType, Object payload) {
        return EventEnvelope.of(eventType, userId.toString(), userId, "corr-1", payload);
    }
}
