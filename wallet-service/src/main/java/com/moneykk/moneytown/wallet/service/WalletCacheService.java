package com.moneykk.moneytown.wallet.service;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.wallet.dto.response.WalletResponse;
import com.moneykk.moneytown.wallet.entity.Wallet;
import com.moneykk.moneytown.wallet.global.exception.WalletErrorCode;
import com.moneykk.moneytown.wallet.global.config.WalletRedisCacheConfig;
import com.moneykk.moneytown.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// @Cacheable을 별도 빈으로 분리한 이유: 같은 클래스 내부(this.xxx())에서 호출하면
// Spring AOP 프록시를 안 거쳐서 캐시가 전혀 적용되지 않는다(self-invocation 문제).
@Service
@RequiredArgsConstructor
public class WalletCacheService {

    private final WalletRepository walletRepository;

    @Cacheable(cacheNames = WalletRedisCacheConfig.WALLET_CACHE, key = "#userId")
    @Transactional(readOnly = true)
    public WalletResponse getWallet(UUID userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        return WalletResponse.from(wallet);
    }
}
