package com.moneykk.moneytown.offering.global.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class OfferingCacheConfig {

    public static final String AI_PORTFOLIO_CANDIDATES =
            "aiPortfolioCandidates";

    @Bean
    public CacheManager offeringCacheManager(
            @Value(
                    "${offering.ai-candidates.cache.ttl-seconds:5}"
            )
            long ttlSeconds,

            @Value(
                    "${offering.ai-candidates.cache.maximum-size:20}"
            )
            long maximumSize
    ) {
        CaffeineCacheManager cacheManager =
                new CaffeineCacheManager(
                        AI_PORTFOLIO_CANDIDATES
                );

        cacheManager.setCaffeine(
                Caffeine.newBuilder()
                        .expireAfterWrite(
                                Duration.ofSeconds(ttlSeconds)
                        )
                        .maximumSize(maximumSize)
                        .recordStats()
        );

        return cacheManager;
    }
}