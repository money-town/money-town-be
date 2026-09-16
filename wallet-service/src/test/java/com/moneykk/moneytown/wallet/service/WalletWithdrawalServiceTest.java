package com.moneykk.moneytown.wallet.service;

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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletWithdrawalServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private WalletWithdrawalService walletWithdrawalService;

    private final UUID userId = UUID.randomUUID();
    private final UUID withdrawnBy = UUID.randomUUID();

    @Test
    @DisplayName("지갑이 있으면 논리삭제하고 탈퇴 처리자를 기록한다")
    void handleUserWithdrawn_walletExists_softDeletes() {
        Wallet wallet = new Wallet(userId);
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));

        walletWithdrawalService.handleUserWithdrawn(userId, withdrawnBy);

        assertTrue(wallet.isDeleted());
        assertEquals(withdrawnBy, wallet.getDeletedBy());
    }

    @Test
    @DisplayName("이미 논리삭제된 지갑이면 다시 처리하지 않는다 (중복 수신 대비)")
    void handleUserWithdrawn_alreadyDeleted_isIdempotent() {
        Wallet wallet = new Wallet(userId);
        wallet.softDelete(UUID.randomUUID());
        var originalDeletedAt = wallet.getDeletedAt();
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));

        walletWithdrawalService.handleUserWithdrawn(userId, withdrawnBy);

        assertEquals(originalDeletedAt, wallet.getDeletedAt());
    }

    @Test
    @DisplayName("지갑이 없으면 아무 것도 하지 않는다")
    void handleUserWithdrawn_walletNotFound_doesNothing() {
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> walletWithdrawalService.handleUserWithdrawn(userId, withdrawnBy));

        verify(walletRepository).findByUserIdForUpdate(userId);
    }
}
