package com.moneykk.moneytown.offering.offering.query.application;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.moneykk.moneytown.offering.global.config.OfferingCacheConfig;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.offering.query.dto.response.AiPortfolioCandidateResponse;
import com.moneykk.moneytown.offering.offering.query.repository.OfferingQueryRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(
        classes = OfferingAiCandidateCacheTest.CacheTestConfig.class
)
class OfferingAiCandidateCacheTest {

    private static final int LIMIT = 10;
    private static final int CONCURRENT_REQUESTS = 20;

    @Autowired
    private OfferingQueryService offeringQueryService;

    @Autowired
    private OfferingQueryRepository offeringQueryRepository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        reset(offeringQueryRepository);

        Cache cache = cacheManager.getCache(
                OfferingCacheConfig.AI_PORTFOLIO_CANDIDATES
        );

        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    @DisplayName("동일한 AI 공모 후보 동시 요청은 DB 조회 한 번으로 합쳐진다")
    void combinesConcurrentRequestsIntoSingleRepositoryCall()
            throws Exception {
        // given
        AiPortfolioCandidateResponse candidate =
                new AiPortfolioCandidateResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "강남 오피스텔 조각투자 1차 공모",
                        100_000L,
                        100_000L,
                        7_600L,
                        Instant.parse("2026-09-10T09:00:00Z"),
                        Instant.parse("2026-09-20T09:00:00Z")
                );

        List<AiPortfolioCandidateResponse> candidates =
                List.of(candidate);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch repositoryEntered = new CountDownLatch(1);
        CountDownLatch releaseRepository = new CountDownLatch(1);

        when(offeringQueryRepository.findAiPortfolioCandidates(
                any(Instant.class),
                eq(LIMIT)
        )).thenAnswer(invocation -> {
            repositoryEntered.countDown();

            boolean released =
                    releaseRepository.await(
                            3,
                            TimeUnit.SECONDS
                    );

            assertTrue(released);

            return candidates;
        });

        ExecutorService executorService =
                Executors.newFixedThreadPool(CONCURRENT_REQUESTS);

        List<Future<List<AiPortfolioCandidateResponse>>> futures =
                new ArrayList<>();

        try {
            for (int index = 0;
                 index < CONCURRENT_REQUESTS;
                 index++) {

                futures.add(
                        executorService.submit(() -> {
                            startLatch.await();

                            return offeringQueryService
                                    .getAiPortfolioCandidates(LIMIT);
                        })
                );
            }

            // when
            startLatch.countDown();

            assertTrue(
                    repositoryEntered.await(
                            3,
                            TimeUnit.SECONDS
                    )
            );

            releaseRepository.countDown();

            // then
            for (Future<List<AiPortfolioCandidateResponse>> future
                    : futures) {

                assertThat(
                        future.get(3, TimeUnit.SECONDS)
                ).containsExactly(candidate);
            }

            verify(
                    offeringQueryRepository,
                    times(1)
            ).findAiPortfolioCandidates(
                    any(Instant.class),
                    eq(LIMIT)
            );
        } finally {
            releaseRepository.countDown();
            executorService.shutdownNow();
        }
    }

    @Configuration
    @EnableCaching
    static class CacheTestConfig {

        @Bean
        CacheManager cacheManager() {
            CaffeineCacheManager cacheManager =
                    new CaffeineCacheManager(
                            OfferingCacheConfig
                                    .AI_PORTFOLIO_CANDIDATES
                    );

            cacheManager.setCaffeine(
                    Caffeine.newBuilder()
                            .expireAfterWrite(
                                    Duration.ofMinutes(1)
                            )
                            .maximumSize(20)
            );

            return cacheManager;
        }

        @Bean
        OfferingRepository offeringRepository() {
            return Mockito.mock(
                    OfferingRepository.class
            );
        }

        @Bean
        OfferingQueryRepository offeringQueryRepository() {
            return Mockito.mock(
                    OfferingQueryRepository.class
            );
        }

        @Bean
        SubscriptionRepository subscriptionRepository() {
            return Mockito.mock(
                    SubscriptionRepository.class
            );
        }

        @Bean
        OfferingQueryService offeringQueryService(
                OfferingRepository offeringRepository,
                OfferingQueryRepository offeringQueryRepository,
                SubscriptionRepository subscriptionRepository
        ) {
            return new OfferingQueryService(
                    offeringRepository,
                    offeringQueryRepository,
                    subscriptionRepository
            );
        }
    }
}