package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.command.config.SubscriptionConfirmationProperties;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import com.moneykk.moneytown.offering.subscription.monitoring.SubscriptionBatchConfirmationMetrics;
import com.moneykk.moneytown.offering.subscription.monitoring.SubscriptionLifecycleMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
@Import({
        SubscriptionBatchConfirmationService.class,
        SubscriptionConfirmationBatchTransactionService.class,
        SubscriptionConfirmationProperties.class
})
class SubscriptionConfirmationBatchIntegrationTest {

    private static final int TOTAL_SUBSCRIPTION_COUNT = 250;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasourceProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                POSTGRES::getJdbcUrl
        );

        registry.add(
                "spring.datasource.username",
                POSTGRES::getUsername
        );

        registry.add(
                "spring.datasource.password",
                POSTGRES::getPassword
        );

        registry.add(
                "spring.jpa.hibernate.ddl-auto",
                () -> "validate"
        );

        registry.add(
                "spring.flyway.enabled",
                () -> true
        );

        registry.add(
                "subscription.confirmation.batch-size",
                () -> 100
        );
    }

    @Autowired
    private SubscriptionConfirmationBatchTransactionService
            confirmationBatchTransactionService;

    @Autowired
    private OfferingRepository offeringRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private SubscriptionEventPublisher subscriptionEventPublisher;

    @MockitoBean
    private SubscriptionLifecycleMetrics subscriptionLifecycleMetrics;

    @MockitoBean
    private SubscriptionBatchConfirmationMetrics
            subscriptionBatchConfirmationMetrics;

    @BeforeEach
    void setUp() {
        /*
         * FK 참조 순서를 고려하여 청약을 먼저 삭제한다.
         */
        jdbcTemplate.update(
                "DELETE FROM p_subscriptions"
        );

        jdbcTemplate.update(
                "DELETE FROM p_offerings"
        );
    }

    @Test
    @DisplayName(
            "HOLD_SUCCEEDED 청약 250건을 "
                    + "100건, 100건, 50건의 독립 배치로 확정한다"
    )
    void confirmsTwoHundredFiftySubscriptionsInThreeBatches() {
        // given
        UUID offeringId =
                insertSoldOutOffering(
                        TOTAL_SUBSCRIPTION_COUNT
                );

        insertSubscriptions(
                offeringId,
                "HOLD_SUCCEEDED",
                TOTAL_SUBSCRIPTION_COUNT
        );

        // when
        int firstBatch =
                confirmationBatchTransactionService
                        .confirmNextBatch();

        long confirmedAfterFirstBatch =
                countSubscriptionsByStatus(
                        offeringId,
                        "CONFIRMED"
                );

        int secondBatch =
                confirmationBatchTransactionService
                        .confirmNextBatch();

        long confirmedAfterSecondBatch =
                countSubscriptionsByStatus(
                        offeringId,
                        "CONFIRMED"
                );

        int thirdBatch =
                confirmationBatchTransactionService
                        .confirmNextBatch();

        long confirmedAfterThirdBatch =
                countSubscriptionsByStatus(
                        offeringId,
                        "CONFIRMED"
                );

        int noRemainingBatch =
                confirmationBatchTransactionService
                        .confirmNextBatch();

        // then
        assertThat(firstBatch)
                .isEqualTo(100);

        assertThat(confirmedAfterFirstBatch)
                .isEqualTo(100);

        assertThat(secondBatch)
                .isEqualTo(100);

        assertThat(confirmedAfterSecondBatch)
                .isEqualTo(200);

        assertThat(thirdBatch)
                .isEqualTo(50);

        assertThat(confirmedAfterThirdBatch)
                .isEqualTo(TOTAL_SUBSCRIPTION_COUNT);

        assertThat(noRemainingBatch)
                .isZero();

        assertThat(
                countSubscriptionsByStatus(
                        offeringId,
                        "HOLD_SUCCEEDED"
                )
        ).isZero();

        /*
         * 각 청약은 정확히 한 번씩 확정 이벤트 생성을 요청해야 한다.
         *
         * 네 번째 빈 배치에서 추가 호출이 발생하면
         * 전체 호출 횟수가 250을 초과하므로 이 검증이 실패한다.
         */
        verify(
                subscriptionEventPublisher,
                times(TOTAL_SUBSCRIPTION_COUNT)
        ).publishConfirmed(
                any(Subscription.class),
                any(UUID.class),
                anyString()
        );
    }

    @Test
    @DisplayName(
            "PROCESSING 청약이 하나라도 남아 있으면 "
                    + "HOLD_SUCCEEDED 청약을 확정하지 않는다"
    )
    void doesNotConfirmWhileProcessingSubscriptionRemains() {
        // given
        UUID offeringId =
                insertSoldOutOffering(2);

        insertSubscriptions(
                offeringId,
                "HOLD_SUCCEEDED",
                1
        );

        insertSubscriptions(
                offeringId,
                "PROCESSING",
                1
        );

        // when
        int confirmedCount =
                confirmationBatchTransactionService
                        .confirmNextBatch();

        // then
        assertThat(confirmedCount)
                .isZero();

        assertThat(
                countSubscriptionsByStatus(
                        offeringId,
                        "HOLD_SUCCEEDED"
                )
        ).isEqualTo(1);

        assertThat(
                countSubscriptionsByStatus(
                        offeringId,
                        "PROCESSING"
                )
        ).isEqualTo(1);

        verify(
                subscriptionEventPublisher,
                never()
        ).publishConfirmed(
                any(Subscription.class),
                any(UUID.class),
                anyString()
        );
    }

    @Test
    @DisplayName(
            "한 트랜잭션이 공모를 잠그고 있으면 "
                    + "다른 트랜잭션은 해당 공모를 건너뛴다"
    )
    void skipsOfferingLockedByAnotherTransaction()
            throws Exception {

        // given
        UUID firstOfferingId =
                insertSoldOutOffering(1);

        UUID secondOfferingId =
                insertSoldOutOffering(1);

        insertSubscriptions(
                firstOfferingId,
                "HOLD_SUCCEEDED",
                1
        );

        insertSubscriptions(
                secondOfferingId,
                "HOLD_SUCCEEDED",
                1
        );

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch firstLockAcquired =
                new CountDownLatch(1);

        CountDownLatch releaseFirstTransaction =
                new CountDownLatch(1);

        try {
            /*
             * 첫 번째 트랜잭션은 공모 한 건을 선점하고
             * 두 번째 트랜잭션이 실행될 때까지 잠금을 유지한다.
             */
            Future<UUID> firstClaim =
                    executor.submit(
                            () -> new TransactionTemplate(
                                    transactionManager
                            ).execute(
                                    status -> {
                                        Offering offering =
                                                offeringRepository
                                                        .findNextConfirmationTargetForUpdate()
                                                        .orElseThrow();

                                        firstLockAcquired.countDown();

                                        awaitLatch(
                                                releaseFirstTransaction
                                        );

                                        return offering
                                                .getOfferingId();
                                    }
                            )
                    );

            assertThat(
                    firstLockAcquired.await(
                            10,
                            TimeUnit.SECONDS
                    )
            ).isTrue();

            /*
             * 첫 번째 공모가 잠긴 동안 두 번째 트랜잭션을 실행한다.
             *
             * SKIP LOCKED가 적용되지 않았다면 이 조회는
             * 첫 번째 트랜잭션의 잠금 해제를 기다리게 된다.
             */
            Future<UUID> secondClaim =
                    executor.submit(
                            () -> new TransactionTemplate(
                                    transactionManager
                            ).execute(
                                    status -> offeringRepository
                                            .findNextConfirmationTargetForUpdate()
                                            .orElseThrow()
                                            .getOfferingId()
                            )
                    );

            UUID secondClaimedOfferingId =
                    secondClaim.get(
                            10,
                            TimeUnit.SECONDS
                    );

            /*
             * 두 번째 트랜잭션이 다른 공모를 선점한 이후
             * 첫 번째 트랜잭션의 잠금을 해제한다.
             */
            releaseFirstTransaction.countDown();

            UUID firstClaimedOfferingId =
                    firstClaim.get(
                            10,
                            TimeUnit.SECONDS
                    );

            assertThat(firstClaimedOfferingId)
                    .isIn(
                            firstOfferingId,
                            secondOfferingId
                    );

            assertThat(secondClaimedOfferingId)
                    .isIn(
                            firstOfferingId,
                            secondOfferingId
                    );

            assertThat(secondClaimedOfferingId)
                    .isNotEqualTo(
                            firstClaimedOfferingId
                    );

        } finally {
            /*
             * 테스트 실패 시에도 첫 번째 트랜잭션이
             * latch에서 영구 대기하지 않도록 반드시 해제한다.
             */
            releaseFirstTransaction.countDown();

            executor.shutdownNow();

            assertThat(
                    executor.awaitTermination(
                            10,
                            TimeUnit.SECONDS
                    )
            ).isTrue();
        }
    }

    private UUID insertSoldOutOffering(
            long totalQuantity
    ) {
        UUID offeringId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();
        UUID auditorId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO p_offerings (
                    offering_id,
                    asset_id,
                    issuer_id,
                    title,
                    price_per_unit,
                    total_quantity,
                    remaining_quantity,
                    min_subscription_quantity,
                    max_subscription_quantity,
                    start_at,
                    end_at,
                    offering_status,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by,
                    is_deleted
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    10000,
                    ?,
                    0,
                    1,
                    ?,
                    CURRENT_TIMESTAMP - INTERVAL '1 hour',
                    CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    'SOLD_OUT',
                    CURRENT_TIMESTAMP,
                    ?,
                    CURRENT_TIMESTAMP,
                    ?,
                    FALSE
                )
                """,
                offeringId,
                assetId,
                issuerId,
                "청약 확정 배치 통합 테스트 공모",
                totalQuantity,
                totalQuantity,
                auditorId,
                auditorId
        );

        return offeringId;
    }

    private void insertSubscriptions(
            UUID offeringId,
            String subscriptionStatus,
            int count
    ) {
        for (int index = 0; index < count; index++) {
            UUID subscriptionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            jdbcTemplate.update(
                    """
                    INSERT INTO p_subscriptions (
                        subscription_id,
                        offering_id,
                        user_id,
                        quantity,
                        price_per_unit,
                        amount,
                        subscription_status,
                        quantity_reserved,
                        reservation_expires_at,
                        created_at,
                        created_by,
                        updated_at,
                        updated_by,
                        is_deleted
                    )
                    VALUES (
                        ?,
                        ?,
                        ?,
                        1,
                        10000,
                        10000,
                        ?,
                        TRUE,
                        CASE
                            WHEN ? = 'PROCESSING'
                            THEN CURRENT_TIMESTAMP
                                   + INTERVAL '10 minutes'
                            ELSE NULL
                        END,
                        CURRENT_TIMESTAMP,
                        ?,
                        CURRENT_TIMESTAMP,
                        ?,
                        FALSE
                    )
                    """,
                    subscriptionId,
                    offeringId,
                    userId,
                    subscriptionStatus,
                    subscriptionStatus,
                    userId,
                    userId
            );
        }
    }

    private long countSubscriptionsByStatus(
            UUID offeringId,
            String subscriptionStatus
    ) {
        Long count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                          FROM p_subscriptions
                         WHERE offering_id = ?
                           AND subscription_status = ?
                           AND is_deleted = FALSE
                        """,
                        Long.class,
                        offeringId,
                        subscriptionStatus
                );

        assertThat(count)
                .isNotNull();

        return count;
    }

    private void awaitLatch(
            CountDownLatch latch
    ) {
        try {
            if (!latch.await(
                    10,
                    TimeUnit.SECONDS
            )) {
                throw new IllegalStateException(
                        "트랜잭션 잠금 해제 대기시간을 초과했습니다."
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "트랜잭션 잠금 대기 중 인터럽트됐습니다.",
                    e
            );
        }
    }
}
