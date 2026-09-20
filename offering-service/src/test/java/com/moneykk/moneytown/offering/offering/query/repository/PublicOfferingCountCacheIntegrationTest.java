package com.moneykk.moneytown.offering.offering.query.repository;

import com.moneykk.moneytown.common.config.QuerydslConfig;
import com.moneykk.moneytown.offering.global.config.OfferingCacheConfig;
import com.moneykk.moneytown.offering.offering.query.dto.request.OfferingSearchCondition;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공개 공모 목록 COUNT 캐시가 설정된 TTL 동안 유지되고,
 * TTL 만료 후 실제 값으로 자동 갱신되는지 실제 DB로 검증한다.
 *
 * OfferingQueryRepositoryImplIntegrationTest와 달리
 * 이 테스트는 Spring이 관리하는 프록시 빈을 통해 호출해야
 * {@code @Cacheable}이 실제로 적용된다 (self-invocation이 아닌
 * 빈 경계 너머의 호출이어야 AOP 프록시가 가로챈다).
 */
@DataJpaTest(properties = {
        "spring.cloud.config.enabled=false",
        "spring.flyway.postgresql.transactional-lock=false",
        "offering.public-count.cache.ttl-seconds=1"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QuerydslConfig.class, OfferingCacheConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class PublicOfferingCountCacheIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OfferingQueryRepository offeringQueryRepository;

    @Autowired
    private CacheManager cacheManager;

    @TestConfiguration
    static class RepositoryTestConfig {

        @Bean
        OfferingQueryRepository offeringQueryRepository(
                JPAQueryFactory queryFactory
        ) {
            return new OfferingQueryRepositoryImpl(queryFactory);
        }
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM p_offerings");

        Cache cache = cacheManager.getCache(
                OfferingCacheConfig.PUBLIC_OFFERING_COUNT
        );

        if (cache != null) {
            cache.clear();
        }
    }

    private void insertOpenOffering() {
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO p_offerings (
                    offering_id, asset_id, issuer_id, title,
                    price_per_unit, total_quantity, remaining_quantity,
                    min_subscription_quantity, max_subscription_quantity,
                    start_at, end_at, offering_status,
                    created_at, created_by, updated_at, updated_by,
                    is_deleted
                ) VALUES (
                    ?, ?, ?, ?, 10000, 100, 100, 1, 100,
                    CURRENT_TIMESTAMP - INTERVAL '100 second',
                    CURRENT_TIMESTAMP + INTERVAL '3600 second',
                    'OPEN', CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, ?, false
                )
                """,
                offeringId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "공모",
                userId,
                userId
        );
    }

    @Test
    @DisplayName("TTL 동안은 캐시된 COUNT를 반환하고, TTL 만료 후 실제 값으로 갱신된다")
    void cachesCountUntilTtlExpires() throws InterruptedException {
        OfferingSearchCondition condition =
                new OfferingSearchCondition(null, null);

        insertOpenOffering();
        insertOpenOffering();

        long first = offeringQueryRepository.countPublicOfferings(condition);
        assertThat(first).isEqualTo(2L);

        // 캐시 TTL(1초) 안에 데이터가 추가되어도 캐시된 값을 그대로 반환해야 한다.
        insertOpenOffering();

        long stillCached = offeringQueryRepository.countPublicOfferings(condition);
        assertThat(stillCached).isEqualTo(2L);

        // TTL 만료 후에는 실제 값으로 갱신된다.
        Thread.sleep(1_200);

        long refreshed = offeringQueryRepository.countPublicOfferings(condition);
        assertThat(refreshed).isEqualTo(3L);
    }

    @Test
    @DisplayName("검색 조건이 다르면 서로 다른 캐시 항목으로 관리된다")
    void cachesSeparatelyPerCondition() {
        insertOpenOffering();
        insertOpenOffering();

        OfferingSearchCondition allCondition =
                new OfferingSearchCondition(null, null);
        OfferingSearchCondition keywordCondition =
                new OfferingSearchCondition(null, "존재하지않는키워드");

        long allCount = offeringQueryRepository.countPublicOfferings(allCondition);
        long keywordCount = offeringQueryRepository.countPublicOfferings(keywordCondition);

        assertThat(allCount).isEqualTo(2L);
        assertThat(keywordCount).isEqualTo(0L);
    }
}
