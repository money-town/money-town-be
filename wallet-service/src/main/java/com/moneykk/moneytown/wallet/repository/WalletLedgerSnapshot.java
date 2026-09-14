package com.moneykk.moneytown.wallet.repository;

public interface WalletLedgerSnapshot {
    Long getWalletId();
    long getBalanceAfter();
}
