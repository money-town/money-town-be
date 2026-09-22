package com.moneykk.moneytown.wallet.service;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.wallet.entity.Wallet;
import com.moneykk.moneytown.wallet.global.config.WalletRedisCacheConfig;
import com.moneykk.moneytown.wallet.global.exception.WalletErrorCode;
import com.moneykk.moneytown.wallet.repository.WalletRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// @Cacheable이 실제로 프록시를 거쳐 동작하는지(self-invocation 문제 없는지) 검증하는 통합 성격 테스트.
// 실제 Redis 대신 ConcurrentMapCacheManager로 캐시 동작만 검증한다.
class WalletCacheServiceTest {

    private AnnotationConfigApplicationContext context;
    private WalletRepository walletRepository;
    private WalletCacheService walletCacheService;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        walletRepository = context.getBean(WalletRepository.class);
        walletCacheService = context.getBean(WalletCacheService.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    @DisplayName("같은 사용자의 지갑을 두 번 조회하면 저장소는 한 번만 호출한다")
    void cachesWallet() {
        UUID userId = UUID.randomUUID();
        Wallet wallet = walletWithId(userId, 1L);
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

        var first = walletCacheService.getWallet(userId);
        var second = walletCacheService.getWallet(userId);

        assertEquals(first, second);
        verify(walletRepository, times(1)).findByUserId(userId);
    }

    @Test
    @DisplayName("지갑이 없는 조회 실패는 캐시하지 않는다")
    void doesNotCacheMissingWallet() {
        UUID userId = UUID.randomUUID();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());

        BusinessException first = assertThrows(BusinessException.class,
                () -> walletCacheService.getWallet(userId));
        BusinessException second = assertThrows(BusinessException.class,
                () -> walletCacheService.getWallet(userId));

        assertEquals(WalletErrorCode.WALLET_NOT_FOUND, first.getErrorCode());
        assertEquals(WalletErrorCode.WALLET_NOT_FOUND, second.getErrorCode());
        verify(walletRepository, times(2)).findByUserId(userId);
    }

    @Test
    @DisplayName("서로 다른 사용자는 캐시 키가 분리된다")
    void cachesPerUser() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        when(walletRepository.findByUserId(userId1)).thenReturn(Optional.of(walletWithId(userId1, 1L)));
        when(walletRepository.findByUserId(userId2)).thenReturn(Optional.of(walletWithId(userId2, 2L)));

        walletCacheService.getWallet(userId1);
        walletCacheService.getWallet(userId2);

        verify(walletRepository, times(1)).findByUserId(userId1);
        verify(walletRepository, times(1)).findByUserId(userId2);
    }

    private Wallet walletWithId(UUID userId, Long id) {
        Wallet wallet = new Wallet(userId);
        ReflectionTestUtils.setField(wallet, "id", id);
        return wallet;
    }

    @Configuration
    @EnableCaching
    static class TestConfig {

        @Bean
        WalletRepository walletRepository() {
            return mock(WalletRepository.class);
        }

        @Bean
        WalletCacheService walletCacheService(WalletRepository walletRepository) {
            return new WalletCacheService(walletRepository);
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(WalletRedisCacheConfig.WALLET_CACHE);
        }
    }
}
