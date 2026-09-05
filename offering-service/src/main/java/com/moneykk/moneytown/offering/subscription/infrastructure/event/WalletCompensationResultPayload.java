package com.moneykk.moneytown.offering.subscription.infrastructure.event;

public record WalletCompensationResultPayload(
        Long holdId,
        Long walletId,
        String compensationType,
        Long transactionId,
        Long amount,
        String reason
) {}