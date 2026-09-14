package com.moneykk.moneytown.wallet.service;

import com.moneykk.moneytown.wallet.entity.Wallet;
import com.moneykk.moneytown.wallet.repository.WalletLedgerSnapshot;
import com.moneykk.moneytown.wallet.repository.WalletRepository;
import com.moneykk.moneytown.wallet.repository.WalletTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletLedgerReconciliationServiceTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    private WalletLedgerReconciliationService reconciliationService;

    @Test
    @DisplayName("모든 지갑의 잔액이 원장 마지막 거래의 balance_after와 같으면 불일치가 없다")
    void findMismatches_allConsistent_returnsEmpty() {
        reconciliationService = new WalletLedgerReconciliationService(walletRepository, walletTransactionRepository);
        when(walletRepository.findAll()).thenReturn(List.of(walletWithBalance(1L, 10_000L), walletWithBalance(2L, 0L)));
        when(walletTransactionRepository.findLatestBalanceAfterPerWallet())
                .thenReturn(List.of(snapshot(1L, 10_000L)));

        List<WalletBalanceMismatch> mismatches = reconciliationService.findMismatches();

        assertThat(mismatches).isEmpty();
    }

    @Test
    @DisplayName("지갑 잔액이 원장 마지막 거래의 balance_after와 다르면 불일치로 잡는다")
    void findMismatches_driftedWallet_returnsMismatch() {
        reconciliationService = new WalletLedgerReconciliationService(walletRepository, walletTransactionRepository);
        when(walletRepository.findAll()).thenReturn(List.of(walletWithBalance(1L, 9_999L)));
        when(walletTransactionRepository.findLatestBalanceAfterPerWallet())
                .thenReturn(List.of(snapshot(1L, 10_000L)));

        List<WalletBalanceMismatch> mismatches = reconciliationService.findMismatches();

        assertThat(mismatches).containsExactly(new WalletBalanceMismatch(1L, 9_999L, 10_000L));
    }

    @Test
    @DisplayName("원장에 거래가 하나도 없는 지갑은 잔액 0을 기대값으로 비교한다")
    void findMismatches_walletWithoutTransactions_comparesAgainstZero() {
        reconciliationService = new WalletLedgerReconciliationService(walletRepository, walletTransactionRepository);
        when(walletRepository.findAll()).thenReturn(List.of(walletWithBalance(1L, 0L)));
        when(walletTransactionRepository.findLatestBalanceAfterPerWallet()).thenReturn(List.of());

        List<WalletBalanceMismatch> mismatches = reconciliationService.findMismatches();

        assertThat(mismatches).isEmpty();
    }

    private Wallet walletWithBalance(Long id, long balance) {
        Wallet wallet = new Wallet(UUID.randomUUID());
        ReflectionTestUtils.setField(wallet, "id", id);
        ReflectionTestUtils.setField(wallet, "balance", balance);
        return wallet;
    }

    private WalletLedgerSnapshot snapshot(Long walletId, long balanceAfter) {
        return new WalletLedgerSnapshot() {
            @Override
            public Long getWalletId() {
                return walletId;
            }

            @Override
            public long getBalanceAfter() {
                return balanceAfter;
            }
        };
    }
}
