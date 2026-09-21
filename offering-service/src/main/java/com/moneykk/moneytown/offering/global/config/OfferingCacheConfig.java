package com.moneykk.moneytown.offering.global.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.actuate.metrics.cache.CacheMetricsRegistrar;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class OfferingCacheConfig {

    public static final String AI_PORTFOLIO_CANDIDATES =
            "aiPortfolioCandidates";

    /**
     * 공개 공모 목록의 전체 건수(COUNT) 캐시.
     *
     * COUNT는 조건에 매치되는 행 수에 비례해 비용이 커지는 반면
     * (LIMIT이 걸린 목록 SELECT와 달리 조건에 맞는 모든 행을 순회해야 함),
     * 짧은 TTL 동안은 약간의 지연을 감수하고 캐시된 값을 재사용해
     * 동시 요청마다 COUNT를 반복 실행하는 것을 피한다.
     */
    public static final String PUBLIC_OFFERING_COUNT =
            "publicOfferingCount";

    @Bean
    public CacheManager offeringCacheManager(
            @Value(
                    "${offering.ai-candidates.cache.ttl-seconds:5}"
            )
            long aiCandidatesTtlSeconds,

            @Value(
                    "${offering.ai-candidates.cache.maximum-size:20}"
            )
            long aiCandidatesMaximumSize,

            @Value(
                    "${offering.public-count.cache.ttl-seconds:5}"
            )
            long publicCountTtlSeconds,

            @Value(
                    "${offering.public-count.cache.maximum-size:100}"
            )
            long publicCountMaximumSize
    ) {
        CaffeineCacheManager cacheManager =
                new CaffeineCacheManager();

        cacheManager.registerCustomCache(
                AI_PORTFOLIO_CANDIDATES,
                Caffeine.newBuilder()
                        .expireAfterWrite(
                                Duration.ofSeconds(aiCandidatesTtlSeconds)
                        )
                        .maximumSize(aiCandidatesMaximumSize)
                        .recordStats()
                        .build()
        );

        cacheManager.registerCustomCache(
                PUBLIC_OFFERING_COUNT,
                Caffeine.newBuilder()
                        .expireAfterWrite(
                                Duration.ofSeconds(publicCountTtlSeconds)
                        )
                        .maximumSize(publicCountMaximumSize)
                        .recordStats()
                        .build()
        );

        return cacheManager;
    }

    @Bean
    public ApplicationRunner offeringCacheMetricsBinder(
            @Qualifier("offeringCacheManager")
            CacheManager cacheManager,
            ObjectProvider<CacheMetricsRegistrar> registrarProvider
    ) {
        return args -> registrarProvider.ifAvailable(
                registrar -> List.of(
                                AI_PORTFOLIO_CANDIDATES,
                                PUBLIC_OFFERING_COUNT
                        )
                        .forEach(cacheName -> {
                            Cache cache = cacheManager.getCache(cacheName);

                            if (cache != null) {
                                registrar.bindCacheToRegistry(cache);
                            }
                        })
        );
    }
}