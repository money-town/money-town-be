package com.moneykk.moneytown.wallet.consumer;

import com.moneykk.moneytown.wallet.consumer.dto.UserRegisteredEvent;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class UserRegisteredConsumerTest {

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private UserRegisteredConsumer userRegisteredConsumer;

    @Test
    @DisplayName("같은 회원가입 이벤트를 중복 수신해도 지갑은 한 번만 생성한다")
    void duplicateEventCreatesWalletOnce() {
        UUID userId = UUID.randomUUID();
        UserRegisteredEvent event = new UserRegisteredEvent(
                UUID.randomUUID(),
                "UserRegistered",
                userId
        );

        given(walletRepository.findByUserId(userId))
                .willReturn(
                        Optional.empty(),
                        Optional.of(new Wallet(userId))
                );

        userRegisteredConsumer.onUserRegistered(event);
        userRegisteredConsumer.onUserRegistered(event);

        then(walletRepository)
                .should(times(1))
                .save(any(Wallet.class));
    }
}
