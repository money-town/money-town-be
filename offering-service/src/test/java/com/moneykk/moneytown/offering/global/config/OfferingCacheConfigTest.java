package com.moneykk.moneytown.offering.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.actuate.metrics.cache.CacheMetricsRegistrar;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class OfferingCacheConfigTest {

    private final OfferingCacheConfig offeringCacheConfig =
            new OfferingCacheConfig();

    @Test
    @DisplayName("AI 포트폴리오 후보 캐시를 설정된 TTL/최대 크기로 생성한다")
    void createsCacheManagerWithConfiguredTtlAndSize() {
        // when
        CacheManager cacheManager =
                offeringCacheConfig.offeringCacheManager(5L, 20L);

        // then
        assertThat(cacheManager)
                .isInstanceOf(CaffeineCacheManager.class);
        assertThat(cacheManager.getCacheNames())
                .containsExactly(OfferingCacheConfig.AI_PORTFOLIO_CANDIDATES);
        assertThat(cacheManager.getCache(
                OfferingCacheConfig.AI_PORTFOLIO_CANDIDATES
        )).isNotNull();
    }

    @Test
    @DisplayName("캐시가 존재하면 캐시 메트릭 레지스트라에 바인딩한다")
    void bindsCacheMetricsWhenCacheExists() throws Exception {
        // given
        CacheManager cacheManager =
                offeringCacheConfig.offeringCacheManager(5L, 20L);

        CacheMetricsRegistrar registrar = mock(CacheMetricsRegistrar.class);

        ObjectProvider<CacheMetricsRegistrar> registrarProvider =
                mock(ObjectProvider.class);

        doAnswer(invocation -> {
            Consumer<CacheMetricsRegistrar> consumer = invocation.getArgument(0);
            consumer.accept(registrar);
            return null;
        }).when(registrarProvider).ifAvailable(any());

        ApplicationRunner runner = offeringCacheConfig
                .offeringCacheMetricsBinder(cacheManager, registrarProvider);

        // when
        runner.run(null);

        // then
        Cache cache = cacheManager.getCache(
                OfferingCacheConfig.AI_PORTFOLIO_CANDIDATES
        );
        verify(registrar).bindCacheToRegistry(cache);
    }

    @Test
    @DisplayName("캐시가 없으면 메트릭을 바인딩하지 않는다")
    void skipsBindingWhenCacheMissing() throws Exception {
        // given
        CacheManager cacheManager = mock(CacheManager.class);
        when(cacheManager.getCache(OfferingCacheConfig.AI_PORTFOLIO_CANDIDATES))
                .thenReturn(null);

        CacheMetricsRegistrar registrar = mock(CacheMetricsRegistrar.class);

        ObjectProvider<CacheMetricsRegistrar> registrarProvider =
                mock(ObjectProvider.class);

        doAnswer(invocation -> {
            Consumer<CacheMetricsRegistrar> consumer = invocation.getArgument(0);
            consumer.accept(registrar);
            return null;
        }).when(registrarProvider).ifAvailable(any());

        ApplicationRunner runner = offeringCacheConfig
                .offeringCacheMetricsBinder(cacheManager, registrarProvider);

        // when
        runner.run(null);

        // then
        verify(registrar, never()).bindCacheToRegistry(any());
    }

    @Test
    @DisplayName("메트릭 레지스트라 빈이 없으면 캐시를 조회하지 않는다")
    void skipsWhenRegistrarUnavailable() throws Exception {
        // given
        CacheManager cacheManager = mock(CacheManager.class);

        ObjectProvider<CacheMetricsRegistrar> registrarProvider =
                mock(ObjectProvider.class);

        ApplicationRunner runner = offeringCacheConfig
                .offeringCacheMetricsBinder(cacheManager, registrarProvider);

        // when
        runner.run(null);

        // then
        verify(cacheManager, never()).getCache(any());
    }
}
