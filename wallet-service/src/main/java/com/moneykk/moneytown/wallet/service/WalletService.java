package com.moneykk.moneytown.wallet.service;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.wallet.dto.response.WalletResponse;
import com.moneykk.moneytown.wallet.dto.response.WalletStatusResponse;
import com.moneykk.moneytown.wallet.entity.Wallet;
import com.moneykk.moneytown.wallet.entity.WalletHoldStatus;
import com.moneykk.moneytown.wallet.global.exception.WalletErrorCode;
import com.moneykk.moneytown.wallet.repository.WalletHoldRepository;
import com.moneykk.moneytown.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletHoldRepository walletHoldRepository;

    public WalletResponse getMyWallet(UUID userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        return WalletResponse.from(wallet);
    }

    public WalletStatusResponse getWalletStatus(UUID userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        boolean hasActiveHold = walletHoldRepository.existsByWalletIdAndStatus(wallet.getId(), WalletHoldStatus.HELD);

        return WalletStatusResponse.of(wallet, hasActiveHold);
    }
}
