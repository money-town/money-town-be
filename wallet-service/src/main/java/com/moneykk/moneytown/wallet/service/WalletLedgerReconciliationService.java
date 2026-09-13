package com.moneykk.moneytown.wallet.service;

import com.moneykk.moneytown.wallet.entity.Wallet;
import com.moneykk.moneytown.wallet.repository.WalletLedgerSnapshot;
import com.moneykk.moneytown.wallet.repository.WalletRepository;
import com.moneykk.moneytown.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

// 거래가 없는 지갑은 기대값 0으로 비교한다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WalletLedgerReconciliationService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    public List<WalletBalanceMismatch> findMismatches() {
        Map<Long, Long> latestLedgerBalanceByWallet = walletTransactionRepository.findLatestBalanceAfterPerWallet()
                .stream()
                .collect(Collectors.toMap(WalletLedgerSnapshot::getWalletId, WalletLedgerSnapshot::getBalanceAfter));

        return walletRepository.findAll().stream()
                .map(wallet -> toMismatchOrNull(wallet, latestLedgerBalanceByWallet))
                .filter(Objects::nonNull)
                .toList();
    }

    private WalletBalanceMismatch toMismatchOrNull(Wallet wallet, Map<Long, Long> latestLedgerBalanceByWallet) {
        long ledgerBalance = latestLedgerBalanceByWallet.getOrDefault(wallet.getId(), 0L);
        if (wallet.getBalance() == ledgerBalance) {
            return null;
        }
        return new WalletBalanceMismatch(wallet.getId(), wallet.getBalance(), ledgerBalance);
    }
}
