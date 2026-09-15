package com.moneykk.moneytown.wallet.service;

public record WalletBalanceMismatch(Long walletId, long walletBalance, long ledgerBalance) {
}
