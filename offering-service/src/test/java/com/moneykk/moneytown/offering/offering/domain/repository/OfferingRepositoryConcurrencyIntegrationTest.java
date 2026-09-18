package com.moneykk.moneytown.offering.offering.domain.repository;

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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class OfferingRepositoryConcurrencyIntegrationTest {

    private static final int CONCURRENT_THREAD_COUNT = 20;

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
                "spring.datasource.hikari.maximum-pool-size",
                () -> CONCURRENT_THREAD_COUNT
        );

        registry.add(
                "spring.datasource.hikari.connection-timeout",
                () -> 5_000L
        );

        registry.add(
                "spring.jpa.hibernate.ddl-auto",
                () -> "validate"
        );

        registry.add(
                "spring.flyway.enabled",
                () -> true
        );
    }

    @Autowired
    private OfferingRepository offeringRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.setTimeout(15);

        jdbcTemplate.update(
                "DELETE FROM p_offerings"
        );
    }

    @Test
    @DisplayName(
            "잔여 100주에 10주 청약 20건이 동시에 요청되면 "
                    + "정확히 10건만 성공하고 공모가 매진된다"
    )
    void preventsOversubscriptionUnderConcurrentRequests()
            throws Exception {

        UUID offeringId = insertOpenOffering(100L);

        List<Integer> results = reserveConcurrently(
                offeringId,
                CONCURRENT_THREAD_COUNT,
                10L
        );

        long successfulCount = results.stream()
                .filter(result -> result == 1)
                .count();

        long failedCount = results.stream()
                .filter(result -> result == 0)
                .count();

        assertThat(successfulCount).isEqualTo(10);
        assertThat(failedCount).isEqualTo(10);

        assertThat(findRemainingQuantity(offeringId))
                .isZero();

        assertThat(findOfferingStatus(offeringId))
                .isEqualTo("SOLD_OUT");
    }

    @Test
    @DisplayName(
            "잔여 95주에 10주 청약이 동시에 요청되면 "
                    + "9건만 성공하고 잔여 5주와 OPEN 상태를 유지한다"
    )
    void preservesRemainingQuantityWhenExactDepletionIsImpossible()
            throws Exception {

        UUID offeringId = insertOpenOffering(95L);

        List<Integer> results = reserveConcurrently(
                offeringId,
                CONCURRENT_THREAD_COUNT,
                10L
        );

        long successfulCount = results.stream()
                .filter(result -> result == 1)
                .count();

        long failedCount = results.stream()
                .filter(result -> result == 0)
                .count();

        assertThat(successfulCount).isEqualTo(9);
        assertThat(failedCount).isEqualTo(11);

        assertThat(findRemainingQuantity(offeringId))
                .isEqualTo(5L);

        assertThat(findOfferingStatus(offeringId))
                .isEqualTo("OPEN");
    }

    @Test
    @DisplayName(
            "수량 차감 이후 같은 트랜잭션이 실패하면 "
                    + "수량과 공모 상태가 함께 롤백된다"
    )
    void rollsBackReservedQuantityWhenTransactionFails() {

        UUID offeringId = insertOpenOffering(100L);
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(
                () -> transactionTemplate.executeWithoutResult(
                        status -> {
                            int updatedRows =
                                    offeringRepository.reserveQuantity(
                                            offeringId,
                                            100L,
                                            userId
                                    );

                            assertThat(updatedRows).isEqualTo(1);

                            throw new IllegalStateException(
                                    "청약 저장 실패 재현"
                            );
                        }
                )
        ).isInstanceOf(IllegalStateException.class)
                .hasMessage("청약 저장 실패 재현");

        assertThat(findRemainingQuantity(offeringId))
                .isEqualTo(100L);

        assertThat(findOfferingStatus(offeringId))
                .isEqualTo("OPEN");
    }

    private List<Integer> reserveConcurrently(
            UUID offeringId,
            int requestCount,
            long quantity
    ) throws Exception {

        ExecutorService executor =
                Executors.newFixedThreadPool(
                        requestCount,
                        task -> {
                            Thread thread = new Thread(
                                    task,
                                    "offering-concurrency-test"
                            );

                            thread.setDaemon(true);

                            return thread;
                        }
                );

        CountDownLatch ready =
                new CountDownLatch(requestCount);

        CountDownLatch start =
                new CountDownLatch(1);

        List<Future<Integer>> futures =
                new ArrayList<>();

        try {
            for (int index = 0;
                 index < requestCount;
                 index++) {

                UUID userId = UUID.randomUUID();

                futures.add(
                        executor.submit(
                                () -> {
                                    ready.countDown();

                                    if (!start.await(
                                            10,
                                            TimeUnit.SECONDS
                                    )) {
                                        throw new IllegalStateException(
                                                "동시 실행 시작 대기시간을 초과했습니다."
                                        );
                                    }

                                    Integer result =
                                            transactionTemplate.execute(
                                                    status -> {
                                                        jdbcTemplate.execute(
                                                                "SET LOCAL lock_timeout = '5s'"
                                                        );

                                                        return offeringRepository
                                                                .reserveQuantity(
                                                                        offeringId,
                                                                        quantity,
                                                                        userId
                                                                );
                                                    }
                                            );

                                    if (result == null) {
                                        throw new IllegalStateException(
                                                "수량 확보 결과가 없습니다."
                                        );
                                    }

                                    return result;
                                }
                        )
                );
            }

            assertThat(
                    ready.await(
                            10,
                            TimeUnit.SECONDS
                    )
            ).isTrue();

            start.countDown();

            List<Integer> results =
                    new ArrayList<>();

            for (Future<Integer> future : futures) {
                results.add(
                        future.get(
                                30,
                                TimeUnit.SECONDS
                        )
                );
            }

            return results;

        } finally {
            start.countDown();

            for (Future<Integer> future : futures) {
                if (!future.isDone()) {
                    future.cancel(true);
                }
            }

            executor.shutdownNow();

            assertThat(
                    executor.awaitTermination(
                            10,
                            TimeUnit.SECONDS
                    )
            ).isTrue();
        }
    }

    private UUID insertOpenOffering(
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
                    ?,
                    1,
                    ?,
                    CURRENT_TIMESTAMP - INTERVAL '1 hour',
                    CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    'OPEN',
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
                "동시성 테스트 공모",
                totalQuantity,
                totalQuantity,
                totalQuantity,
                auditorId,
                auditorId
        );

        return offeringId;
    }

    private long findRemainingQuantity(
            UUID offeringId
    ) {
        Long remainingQuantity =
                jdbcTemplate.queryForObject(
                        """
                        SELECT remaining_quantity
                          FROM p_offerings
                         WHERE offering_id = ?
                        """,
                        Long.class,
                        offeringId
                );

        assertThat(remainingQuantity).isNotNull();

        return remainingQuantity;
    }

    private String findOfferingStatus(
            UUID offeringId
    ) {
        String offeringStatus =
                jdbcTemplate.queryForObject(
                        """
                        SELECT offering_status
                          FROM p_offerings
                         WHERE offering_id = ?
                        """,
                        String.class,
                        offeringId
                );

        assertThat(offeringStatus).isNotNull();

        return offeringStatus;
    }
}