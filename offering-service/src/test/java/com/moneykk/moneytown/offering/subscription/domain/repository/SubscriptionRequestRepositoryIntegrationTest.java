package com.moneykk.moneytown.offering.subscription.domain.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.offering.global.outbox.OutboxEventRepository;
import com.moneykk.moneytown.offering.global.outbox.OutboxEventStore;
import com.moneykk.moneytown.offering.subscription.command.application.SubscriptionRequestIntakeService;
import com.moneykk.moneytown.offering.subscription.command.dto.request.SubscriptionCreateRequest;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionRequestStatus;
import com.moneykk.moneytown.offering.subscription.domain.service.SubscriptionRequestHasher;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import com.moneykk.moneytown.offering.subscription.monitoring.SubscriptionRequestMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

@DataJpaTest(properties = {
        "spring.cloud.config.enabled=false",
        "spring.flyway.postgresql.transactional-lock=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Timeout(value = 60, unit = TimeUnit.SECONDS)
@Import({
        SubscriptionRequestIntakeService.class,
        SubscriptionRequestHasher.class,
        SubscriptionEventPublisher.class,
        OutboxEventStore.class,
        SubscriptionRequestRepositoryIntegrationTest.JacksonTestConfig.class
})
class SubscriptionRequestRepositoryIntegrationTest {

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

    @Autowired SubscriptionRequestRepository repository;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired SubscriptionRequestIntakeService intakeService;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean SubscriptionRequestMetrics metrics;
    @MockitoSpyBean SubscriptionEventPublisher eventPublisher;

    private UUID offeringId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM p_outbox_events");
        jdbcTemplate.update("DELETE FROM p_subscription_requests");
        jdbcTemplate.update("DELETE FROM p_subscriptions");
        jdbcTemplate.update("DELETE FROM p_offerings");

        offeringId = UUID.randomUUID();
        userId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO p_offerings (
                    offering_id, asset_id, issuer_id, title,
                    price_per_unit, total_quantity, remaining_quantity,
                    min_subscription_quantity, max_subscription_quantity,
                    start_at, end_at, offering_status,
                    created_by, updated_by
                ) VALUES (?, ?, ?, '비동기 접수 테스트',
                    1000, 100, 100, 1, 100,
                    CURRENT_TIMESTAMP - INTERVAL '1 hour',
                    CURRENT_TIMESTAMP + INTERVAL '1 hour', 'OPEN', ?, ?)
                """,
                offeringId, UUID.randomUUID(), issuerId, issuerId, issuerId
        );
    }

    @Test
    void insertsOnlyOnceForSameUserAndIdempotencyKey() {
        UUID firstRequestId = UUID.randomUUID();

        int first = repository.insertIfAbsent(
                firstRequestId, offeringId, userId, 1L,
                "same-key", "a".repeat(64), "correlation"
        );
        int duplicate = repository.insertIfAbsent(
                UUID.randomUUID(), offeringId, userId, 1L,
                "same-key", "a".repeat(64), "correlation"
        );

        assertThat(first).isEqualTo(1);
        assertThat(duplicate).isZero();
        assertThat(repository
                .findByUserIdAndIdempotencyKey(userId, "same-key")
                .orElseThrow()
                .getSubscriptionRequestId())
                .isEqualTo(firstRequestId);
    }

    @Test
    void loadsInsertedRequestWithQueuedStatus() {
        UUID requestId = UUID.randomUUID();
        repository.insertIfAbsent(
                requestId, offeringId, userId, 2L,
                "new-key", "b".repeat(64), "correlation"
        );

        var request = repository.findByIdForUpdate(requestId).orElseThrow();

        assertThat(request.getRequestStatus())
                .isEqualTo(SubscriptionRequestStatus.QUEUED);
        assertThat(request.getQuantity()).isEqualTo(2L);
        assertThat(repository.countByRequestStatus(SubscriptionRequestStatus.QUEUED))
                .isEqualTo(1L);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void storesRequestAndOutboxTogether() {
        var result = intakeService.accept(
                offeringId,
                userId,
                "atomic-key",
                new SubscriptionCreateRequest(3L),
                "correlation"
        );

        assertThat(result.replayed()).isFalse();
        assertThat(repository.count()).isEqualTo(1L);
        assertThat(outboxEventRepository.findAll())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getAggregateId())
                            .isEqualTo(result.response().requestId());
                    assertThat(event.getEventType())
                            .isEqualTo("SubscriptionRequested");
                    assertThat(event.getTopic())
                            .isEqualTo("subscription-requested");
                });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollsBackRequestWhenOutboxCreationFails() {
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("outbox failure");
        }).when(eventPublisher).publishRequested(
                org.mockito.ArgumentMatchers.any()
        );

        assertThatThrownBy(() -> intakeService.accept(
                offeringId,
                userId,
                "rollback-key",
                new SubscriptionCreateRequest(1L),
                "correlation"
        )).isInstanceOf(IllegalStateException.class);

        assertThat(repository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();
    }

    @TestConfiguration
    static class JacksonTestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
