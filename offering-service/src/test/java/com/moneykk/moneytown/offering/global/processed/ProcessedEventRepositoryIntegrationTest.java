package com.moneykk.moneytown.offering.global.processed;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * ProcessedEventRepository는 클래스 레벨에 @Transactional(MANDATORY)가 있어
 * 이미 진행 중인 트랜잭션 안에서만 호출할 수 있다.
 * @DataJpaTest의 기본 동작(테스트 메서드마다 트랜잭션 생성 후 롤백)을
 * 그대로 사용해서 MANDATORY 요건을 만족시킨다.
 */
@DataJpaTest(properties = {
        "spring.cloud.config.enabled=false",
        "spring.flyway.postgresql.transactional-lock=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ProcessedEventRepository.class)
@Testcontainers
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class ProcessedEventRepositoryIntegrationTest {

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
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM p_processed_events");
    }

    @Test
    @DisplayName("최초 (eventId, consumerGroup) 조합은 처리 권한을 확보하고 1을 반환한다")
    void insertIfAbsentInsertsNewRowOnFirstCall() {
        UUID eventId = UUID.randomUUID();

        int inserted = processedEventRepository.insertIfAbsent(
                eventId, "offering-service", "SubscriptionConfirmed", UUID.randomUUID()
        );

        assertThat(inserted).isEqualTo(1);
    }

    @Test
    @DisplayName("동일한 (eventId, consumerGroup)으로 재호출하면 0을 반환한다")
    void insertIfAbsentReturnsZeroForDuplicateCall() {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();

        processedEventRepository.insertIfAbsent(
                eventId, "offering-service", "SubscriptionConfirmed", aggregateId
        );

        int secondAttempt = processedEventRepository.insertIfAbsent(
                eventId, "offering-service", "SubscriptionConfirmed", aggregateId
        );

        assertThat(secondAttempt).isZero();
    }

    @Test
    @DisplayName("같은 eventId라도 consumerGroup이 다르면 별도로 처리 권한을 확보한다")
    void insertIfAbsentAllowsDifferentConsumerGroupsForSameEvent() {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();

        int first = processedEventRepository.insertIfAbsent(
                eventId, "offering-service", "SubscriptionConfirmed", aggregateId
        );

        int second = processedEventRepository.insertIfAbsent(
                eventId, "analysis-service", "SubscriptionConfirmed", aggregateId
        );

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(1);
    }

    @Test
    @DisplayName("처리 이력이 존재하면 완료 시각을 갱신하고 1을 반환한다")
    void markCompletedUpdatesExistingRow() {
        UUID eventId = UUID.randomUUID();

        processedEventRepository.insertIfAbsent(
                eventId, "offering-service", "SubscriptionConfirmed", UUID.randomUUID()
        );

        int completed = processedEventRepository.markCompleted(
                eventId, "offering-service"
        );

        assertThat(completed).isEqualTo(1);
    }

    @Test
    @DisplayName("처리 이력이 없으면 완료 처리는 0을 반환한다")
    void markCompletedReturnsZeroWhenRowNotFound() {
        int completed = processedEventRepository.markCompleted(
                UUID.randomUUID(), "offering-service"
        );

        assertThat(completed).isZero();
    }
}
