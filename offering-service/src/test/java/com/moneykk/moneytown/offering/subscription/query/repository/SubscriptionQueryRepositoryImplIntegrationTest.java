package com.moneykk.moneytown.offering.subscription.query.repository;

import com.moneykk.moneytown.common.config.QuerydslConfig;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.query.dto.request.SubscriptionSearchCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.cloud.config.enabled=false",
        "spring.flyway.postgresql.transactional-lock=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(QuerydslConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class SubscriptionQueryRepositoryImplIntegrationTest {

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

    private SubscriptionQueryRepositoryImpl subscriptionQueryRepository;

    @Autowired
    private com.querydsl.jpa.impl.JPAQueryFactory queryFactory;

    private UUID sharedOfferingId;

    @BeforeEach
    void setUp() {
        subscriptionQueryRepository = new SubscriptionQueryRepositoryImpl(queryFactory);
        jdbcTemplate.update("DELETE FROM p_subscriptions");
        jdbcTemplate.update("DELETE FROM p_offerings");
        sharedOfferingId = insertOffering();
    }

    private UUID insertOffering() {
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO p_offerings (
                    offering_id, asset_id, issuer_id, title,
                    price_per_unit, total_quantity, remaining_quantity,
                    min_subscription_quantity, max_subscription_quantity,
                    start_at, end_at, offering_status,
                    created_at, created_by, updated_at, updated_by, is_deleted
                ) VALUES (
                    ?, ?, ?, ?, 10000, 100, 100, 1, 100,
                    CURRENT_TIMESTAMP - INTERVAL '1 hour',
                    CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    'OPEN', CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, ?, FALSE
                )
                """,
                offeringId, UUID.randomUUID(), UUID.randomUUID(), "테스트 공모",
                userId, userId
        );

        return offeringId;
    }

    private UUID insertSubscription(
            UUID offeringId,
            UUID userId,
            String status,
            boolean deleted
    ) {
        UUID subscriptionId = UUID.randomUUID();
        UUID auditorId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO p_subscriptions (
                    subscription_id, offering_id, user_id,
                    quantity, price_per_unit, amount,
                    subscription_status, quantity_reserved,
                    created_at, created_by, updated_at, updated_by, is_deleted
                ) VALUES (
                    ?, ?, ?, 10, 10000, 100000, ?, TRUE,
                    CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, ?, ?
                )
                """,
                subscriptionId, offeringId, userId, status,
                auditorId, auditorId, deleted
        );

        return subscriptionId;
    }

    private SubscriptionSearchCondition emptyCondition() {
        return new SubscriptionSearchCondition(null, null, null, null);
    }

    @Test
    @DisplayName("본인의 청약만 조회하고 다른 사용자의 청약은 제외한다")
    void searchMySubscriptionsFiltersByUser() {
        UUID userId = UUID.randomUUID();
        UUID mine = insertSubscription(sharedOfferingId, userId, "PROCESSING", false);
        insertSubscription(sharedOfferingId, UUID.randomUUID(), "PROCESSING", false);

        Page<Subscription> result = subscriptionQueryRepository.searchMySubscriptions(
                userId, emptyCondition(), PageRequest.of(0, 10)
        );

        assertThat(result.getContent())
                .extracting(Subscription::getSubscriptionId)
                .containsExactly(mine);
    }

    @Test
    @DisplayName("논리 삭제된 청약은 조회 결과에서 제외한다")
    void searchMySubscriptionsExcludesDeleted() {
        UUID userId = UUID.randomUUID();
        insertSubscription(sharedOfferingId, userId, "PROCESSING", true);
        UUID visible = insertSubscription(insertOffering(), userId, "PROCESSING", false);

        Page<Subscription> result = subscriptionQueryRepository.searchMySubscriptions(
                userId, emptyCondition(), PageRequest.of(0, 10)
        );

        assertThat(result.getContent())
                .extracting(Subscription::getSubscriptionId)
                .containsExactly(visible);
    }

    @Test
    @DisplayName("offeringId 조건을 지정하면 해당 공모의 청약만 조회한다")
    void searchMySubscriptionsFiltersByOfferingId() {
        UUID userId = UUID.randomUUID();
        UUID otherOfferingId = insertOffering();

        UUID matched = insertSubscription(sharedOfferingId, userId, "PROCESSING", false);
        insertSubscription(otherOfferingId, userId, "PROCESSING", false);

        Page<Subscription> result = subscriptionQueryRepository.searchMySubscriptions(
                userId,
                new SubscriptionSearchCondition(sharedOfferingId, null, null, null),
                PageRequest.of(0, 10)
        );

        assertThat(result.getContent())
                .extracting(Subscription::getSubscriptionId)
                .containsExactly(matched);
    }

    @Test
    @DisplayName("subscriptionStatus 조건을 지정하면 해당 상태만 조회한다")
    void searchMySubscriptionsFiltersByStatus() {
        UUID userId = UUID.randomUUID();
        UUID confirmed = insertSubscription(sharedOfferingId, userId, "CONFIRMED", false);
        insertSubscription(insertOffering(), userId, "PROCESSING", false);

        Page<Subscription> result = subscriptionQueryRepository.searchMySubscriptions(
                userId,
                new SubscriptionSearchCondition(null, SubscriptionStatus.CONFIRMED, null, null),
                PageRequest.of(0, 10)
        );

        assertThat(result.getContent())
                .extracting(Subscription::getSubscriptionId)
                .containsExactly(confirmed);
    }

    @Test
    @DisplayName("startDate/endDate 조건으로 청약 접수 기간을 필터링한다")
    void searchMySubscriptionsFiltersByCreatedAtRange() {
        UUID userId = UUID.randomUUID();
        UUID inRange = insertSubscription(sharedOfferingId, userId, "PROCESSING", false);

        Instant now = Instant.now();

        Page<Subscription> resultWithinRange = subscriptionQueryRepository.searchMySubscriptions(
                userId,
                new SubscriptionSearchCondition(
                        null, null,
                        now.minusSeconds(3_600),
                        now.plusSeconds(3_600)
                ),
                PageRequest.of(0, 10)
        );

        assertThat(resultWithinRange.getContent())
                .extracting(Subscription::getSubscriptionId)
                .containsExactly(inRange);

        Page<Subscription> resultOutOfRange = subscriptionQueryRepository.searchMySubscriptions(
                userId,
                new SubscriptionSearchCondition(
                        null, null,
                        now.plusSeconds(3_600),
                        now.plusSeconds(7_200)
                ),
                PageRequest.of(0, 10)
        );

        assertThat(resultOutOfRange.getContent()).isEmpty();
    }

    @Test
    @DisplayName("정렬 조건이 없으면 createdAt desc, subscriptionId desc 순으로 정렬한다")
    void searchMySubscriptionsAppliesDefaultSortWhenUnsorted() throws InterruptedException {
        UUID userId = UUID.randomUUID();
        UUID first = insertSubscription(sharedOfferingId, userId, "PROCESSING", false);
        Thread.sleep(10);
        UUID second = insertSubscription(insertOffering(), userId, "PROCESSING", false);

        Page<Subscription> result = subscriptionQueryRepository.searchMySubscriptions(
                userId, emptyCondition(), PageRequest.of(0, 10)
        );

        assertThat(result.getContent())
                .extracting(Subscription::getSubscriptionId)
                .containsExactly(second, first);
    }

    @Test
    @DisplayName("createdAt 정렬 필드로 정렬할 수 있다")
    void searchMySubscriptionsSortsByCreatedAt() throws InterruptedException {
        UUID userId = UUID.randomUUID();
        UUID first = insertSubscription(sharedOfferingId, userId, "PROCESSING", false);
        Thread.sleep(10);
        UUID second = insertSubscription(insertOffering(), userId, "PROCESSING", false);

        Page<Subscription> result = subscriptionQueryRepository.searchMySubscriptions(
                userId, emptyCondition(),
                PageRequest.of(0, 10, Sort.by(Sort.Order.asc("createdAt")))
        );

        assertThat(result.getContent())
                .extracting(Subscription::getSubscriptionId)
                .containsExactly(first, second);
    }

    @Test
    @DisplayName("허용되지 않은 정렬 필드는 BusinessException을 발생시킨다")
    void searchMySubscriptionsRejectsDisallowedSortField() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.asc("quantity")));

        assertThatThrownBy(() ->
                subscriptionQueryRepository.searchMySubscriptions(
                        userId, emptyCondition(), pageable
                )
        ).isInstanceOf(BusinessException.class);
    }
}
