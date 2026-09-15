package com.moneykk.moneytown.wallet.service;

import com.moneykk.moneytown.wallet.entity.Wallet;
import com.moneykk.moneytown.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletWithdrawalService {

    private final WalletRepository walletRepository;

    // 비관적 락 + isDeleted 조건부 처리로 중복 수신 시 deletedAt이 다시 갱신되지 않게 한다.
    @Transactional
    public void handleUserWithdrawn(UUID userId, UUID withdrawnBy) {
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId).orElse(null);
        if (wallet == null) {
            log.warn("탈퇴 이벤트를 받았지만 지갑이 없습니다. userId={}", userId);
            return;
        }
        if (wallet.isDeleted()) {
            log.info("이미 논리삭제된 지갑이라 스킵합니다. userId={}", userId);
            return;
        }

        wallet.softDelete(withdrawnBy);
        log.info("회원 탈퇴로 지갑을 논리삭제했습니다. userId={}, withdrawnBy={}", userId, withdrawnBy);
    }
}
