package com.moneykk.moneytown.offering.subscription.domain.repository;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.cloud.config.enabled=false",
        "spring.flyway.postgresql.transactional-lock=false"
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class SubscriptionCompensationRecoveryClaimIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasourceProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 4);
        registry.add("spring.datasource.hikari.connection-timeout", () -> 5_000L);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Autowired
    private OfferingRepository offeringRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setTimeout(15);

        jdbcTemplate.update("DELETE FROM p_outbox_events");
        jdbcTemplate.update("DELETE FROM p_subscription_compensations");
        jdbcTemplate.update("DELETE FROM p_subscriptions");
        jdbcTemplate.update("DELETE FROM p_offerings");
    }

    @Test
    @DisplayName("두 트랜잭션은 서로 다른 장기 미완료 보상 공모를 선점한다")
    void concurrentTransactionsClaimDifferentOfferings()
            throws Exception {
        UUID firstOfferingId = insertStuckCompensation();
        UUID secondOfferingId = insertStuckCompensation();
        Instant stuckBefore = Instant.now().minusSeconds(300);

        CountDownLatch firstClaimed = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<UUID> first = executor.submit(() ->
                    transactionTemplate.execute(status -> {
                        Offering claimed = offeringRepository
                                .findNextStuckCompensationTargetForUpdate(
                                        stuckBefore
                                )
                                .orElseThrow();

                        firstClaimed.countDown();

                        try {
                            if (!releaseFirst.await(10, TimeUnit.SECONDS)) {
                                throw new IllegalStateException(
                                        "첫 번째 공모 잠금 해제 대기 실패"
                                );
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }

                        return claimed.getOfferingId();
                    })
            );

            assertThat(firstClaimed.await(10, TimeUnit.SECONDS)).isTrue();

            Future<UUID> second = executor.submit(() ->
                    transactionTemplate.execute(status ->
                            offeringRepository
                                    .findNextStuckCompensationTargetForUpdate(
                                            stuckBefore
                                    )
                                    .orElseThrow()
                                    .getOfferingId()
                    )
            );

            UUID secondClaimedId = second.get(10, TimeUnit.SECONDS);
            releaseFirst.countDown();
            UUID firstClaimedId = first.get(10, TimeUnit.SECONDS);

            assertThat(firstClaimedId).isNotEqualTo(secondClaimedId);
            assertThat(firstClaimedId)
                    .isIn(firstOfferingId, secondOfferingId);
            assertThat(secondClaimedId)
                    .isIn(firstOfferingId, secondOfferingId);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS))
                    .isTrue();
        }
    }

    @Test
    @DisplayName("선점한 공모의 장기 미완료 보상을 설정한 개수만 잠근다")
    void locksOnlyConfiguredBatchSize() {
        UUID offeringId = insertStuckCompensation();
        insertStuckSubscription(offeringId);
        insertStuckSubscription(offeringId);
        Instant stuckBefore = Instant.now().minusSeconds(300);

        int selectedCount = transactionTemplate.execute(status -> {
            Offering offering = offeringRepository
                    .findNextStuckCompensationTargetForUpdate(
                            stuckBefore
                    )
                    .orElseThrow();

            return subscriptionRepository
                    .findStuckCompensationBatchForUpdate(
                            offering.getOfferingId(),
                            stuckBefore,
                            2
                    )
                    .size();
        });

        assertThat(selectedCount).isEqualTo(2);
    }

    private UUID insertStuckCompensation() {
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO p_offerings (
                    offering_id, asset_id, issuer_id, title,
                    price_per_unit, total_quantity, remaining_quantity,
                    min_subscription_quantity, max_subscription_quantity,
                    start_at, end_at, offering_status, cancellation_type,
                    created_at, created_by, updated_at, updated_by,
                    is_deleted
                ) VALUES (
                    ?, ?, ?, ?, 10000, 100, 0, 1, 100,
                    CURRENT_TIMESTAMP - INTERVAL '2 hours',
                    CURRENT_TIMESTAMP - INTERVAL '1 hour',
                    'CANCELLING', 'ADMIN_CANCELLED', CURRENT_TIMESTAMP, ?,
                    CURRENT_TIMESTAMP, ?, FALSE
                )
                """,
                offeringId,
                UUID.randomUUID(),
                userId,
                "보상 복구 선점 통합 테스트 공모",
                userId,
                userId
        );

        insertStuckSubscription(offeringId);

        return offeringId;
    }

    private void insertStuckSubscription(UUID offeringId) {
        UUID subscriptionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO p_subscriptions (
                    subscription_id, offering_id, user_id,
                    quantity, price_per_unit, amount,
                    subscription_status, quantity_reserved,
                    reservation_expires_at, cancellation_type,
                    created_at, created_by, updated_at, updated_by,
                    is_deleted
                ) VALUES (
                    ?, ?, ?, 1, 10000, 10000,
                    'COMPENSATING', TRUE, NULL,
                    'OFFERING_ADMIN_CANCELLED',
                    CURRENT_TIMESTAMP - INTERVAL '10 minutes', ?,
                    CURRENT_TIMESTAMP - INTERVAL '10 minutes', ?, FALSE
                )
                """,
                subscriptionId,
                offeringId,
                userId,
                userId,
                userId
        );

        jdbcTemplate.update(
                """
                INSERT INTO p_subscription_compensations (
                    compensation_id, subscription_id,
                    wallet_status, holding_status,
                    created_at, created_by, updated_at, updated_by
                ) VALUES (
                    ?, ?, 'PENDING', 'PENDING',
                    CURRENT_TIMESTAMP - INTERVAL '10 minutes', ?,
                    CURRENT_TIMESTAMP - INTERVAL '10 minutes', ?
                )
                """,
                UUID.randomUUID(),
                subscriptionId,
                userId,
                userId
        );
    }
}
