package com.moneykk.moneytown.offering.offering.query.repository;

import com.moneykk.moneytown.common.config.QuerydslConfig;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.query.dto.request.OfferingSearchCondition;
import com.moneykk.moneytown.offering.offering.query.dto.response.AiPortfolioCandidateResponse;
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
import java.util.List;
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
class OfferingQueryRepositoryImplIntegrationTest {

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

    private OfferingQueryRepositoryImpl offeringQueryRepository;

    @Autowired
    private com.querydsl.jpa.impl.JPAQueryFactory queryFactory;

    @BeforeEach
    void setUp() {
        offeringQueryRepository = new OfferingQueryRepositoryImpl(queryFactory);
        jdbcTemplate.update("DELETE FROM p_offerings");
    }

    private UUID insertOffering(
            String status,
            String title,
            long remainingQuantity,
            long pricePerUnit,
            long startOffsetSeconds,
            long endOffsetSeconds,
            boolean deleted,
            UUID issuerId
    ) {
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
                    ?, ?, ?, ?, ?, 100, ?, 1, 100,
                    CURRENT_TIMESTAMP
                        + CAST(? AS DOUBLE PRECISION) * INTERVAL '1 second',
                    CURRENT_TIMESTAMP
                        + CAST(? AS DOUBLE PRECISION) * INTERVAL '1 second',
                    ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, ?, ?
                )
                """,
                offeringId,
                UUID.randomUUID(),
                issuerId,
                title,
                pricePerUnit,
                remainingQuantity,
                startOffsetSeconds,
                endOffsetSeconds,
                status,
                userId,
                userId,
                deleted
        );

        return offeringId;
    }

    @Test
    @DisplayName("공개 검색은 PUBLIC 상태(SCHEDULED/OPEN/SOLD_OUT/CLOSED)만 조회한다")
    void searchPublicOfferingsReturnsOnlyPublicStatuses() {
        UUID scheduled = insertOffering("SCHEDULED", "공모1", 100, 10000, -100, 3_600, false, UUID.randomUUID());
        UUID open = insertOffering("OPEN", "공모2", 100, 10000, -100, 3_600, false, UUID.randomUUID());
        insertOffering("DRAFT", "공모3", 100, 10000, -100, 3_600, false, UUID.randomUUID());
        insertOffering("REVIEW_REQUESTED", "공모4", 100, 10000, -100, 3_600, false, UUID.randomUUID());

        Page<Offering> result = offeringQueryRepository.searchPublicOfferings(
                new OfferingSearchCondition(null, null),
                PageRequest.of(0, 10)
        );

        assertThat(result.getContent())
                .extracting(Offering::getOfferingId)
                .containsExactlyInAnyOrder(scheduled, open);
    }

    @Test
    @DisplayName("논리 삭제된 공모는 공개 검색 결과에서 제외한다")
    void searchPublicOfferingsExcludesDeleted() {
        insertOffering("OPEN", "삭제됨", 100, 10000, -100, 3_600, true, UUID.randomUUID());
        UUID visible = insertOffering("OPEN", "노출됨", 100, 10000, -100, 3_600, false, UUID.randomUUID());

        Page<Offering> result = offeringQueryRepository.searchPublicOfferings(
                new OfferingSearchCondition(null, null),
                PageRequest.of(0, 10)
        );

        assertThat(result.getContent())
                .extracting(Offering::getOfferingId)
                .containsExactly(visible);
    }

    @Test
    @DisplayName("keyword로 제목을 대소문자 구분 없이 포함 검색한다")
    void searchPublicOfferingsFiltersByKeywordIgnoringCase() {
        UUID matched = insertOffering("OPEN", "Gangnam Officetel", 100, 10000, -100, 3_600, false, UUID.randomUUID());
        insertOffering("OPEN", "Busan Apartment", 100, 10000, -100, 3_600, false, UUID.randomUUID());

        Page<Offering> result = offeringQueryRepository.searchPublicOfferings(
                new OfferingSearchCondition(null, "gangnam"),
                PageRequest.of(0, 10)
        );

        assertThat(result.getContent())
                .extracting(Offering::getOfferingId)
                .containsExactly(matched);
    }

    @Test
    @DisplayName("offeringStatus 조건을 지정하면 해당 상태만 조회한다")
    void searchPublicOfferingsFiltersByStatus() {
        UUID open = insertOffering("OPEN", "공모1", 100, 10000, -100, 3_600, false, UUID.randomUUID());
        insertOffering("CLOSED", "공모2", 0, 10000, -7_200, -3_600, false, UUID.randomUUID());

        Page<Offering> result = offeringQueryRepository.searchPublicOfferings(
                new OfferingSearchCondition(
                        com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus.OPEN,
                        null
                ),
                PageRequest.of(0, 10)
        );

        assertThat(result.getContent())
                .extracting(Offering::getOfferingId)
                .containsExactly(open);
    }

    @Test
    @DisplayName("searchMyOfferings는 issuerId로 필터링한다")
    void searchMyOfferingsFiltersByIssuer() {
        UUID issuerId = UUID.randomUUID();
        UUID mine = insertOffering("DRAFT", "내 공모", 100, 10000, -100, 3_600, false, issuerId);
        insertOffering("DRAFT", "다른 사람 공모", 100, 10000, -100, 3_600, false, UUID.randomUUID());

        Page<Offering> result = offeringQueryRepository.searchMyOfferings(
                issuerId,
                new OfferingSearchCondition(null, null),
                PageRequest.of(0, 10)
        );

        assertThat(result.getContent())
                .extracting(Offering::getOfferingId)
                .containsExactly(mine);
    }

    @Test
    @DisplayName("searchOfferingsForManagement는 상태 제한 없이 논리 삭제되지 않은 전체를 조회한다")
    void searchOfferingsForManagementReturnsAllNonDeletedStatuses() {
        UUID draft = insertOffering("DRAFT", "공모1", 100, 10000, -100, 3_600, false, UUID.randomUUID());
        UUID rejected = insertOffering("REJECTED", "공모2", 100, 10000, -100, 3_600, false, UUID.randomUUID());
        insertOffering("OPEN", "삭제됨", 100, 10000, -100, 3_600, true, UUID.randomUUID());

        Page<Offering> result = offeringQueryRepository.searchOfferingsForManagement(
                new OfferingSearchCondition(null, null),
                PageRequest.of(0, 10)
        );

        assertThat(result.getContent())
                .extracting(Offering::getOfferingId)
                .containsExactlyInAnyOrder(draft, rejected);
    }

    @Test
    @DisplayName("정렬 조건이 없으면 createdAt desc, offeringId desc 순으로 정렬한다")
    void searchAppliesDefaultSortWhenUnsorted() throws InterruptedException {
        UUID first = insertOffering("OPEN", "공모1", 100, 10000, -100, 3_600, false, UUID.randomUUID());
        Thread.sleep(10);
        UUID second = insertOffering("OPEN", "공모2", 100, 10000, -100, 3_600, false, UUID.randomUUID());

        Page<Offering> result = offeringQueryRepository.searchPublicOfferings(
                new OfferingSearchCondition(null, null),
                PageRequest.of(0, 10)
        );

        assertThat(result.getContent())
                .extracting(Offering::getOfferingId)
                .containsExactly(second, first);
    }

    @Test
    @DisplayName("허용된 정렬 필드(pricePerUnit asc)로 정렬할 수 있다")
    void searchSortsByAllowedField() {
        UUID cheap = insertOffering("OPEN", "공모1", 100, 5_000, -100, 3_600, false, UUID.randomUUID());
        UUID expensive = insertOffering("OPEN", "공모2", 100, 50_000, -100, 3_600, false, UUID.randomUUID());

        Page<Offering> result = offeringQueryRepository.searchPublicOfferings(
                new OfferingSearchCondition(null, null),
                PageRequest.of(0, 10, Sort.by(Sort.Order.asc("pricePerUnit")))
        );

        assertThat(result.getContent())
                .extracting(Offering::getOfferingId)
                .containsExactly(cheap, expensive);
    }

    @Test
    @DisplayName("허용되지 않은 정렬 필드는 BusinessException을 발생시킨다")
    void searchRejectsDisallowedSortField() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.asc("title")));

        assertThatThrownBy(() ->
                offeringQueryRepository.searchPublicOfferings(
                        new OfferingSearchCondition(null, null),
                        pageable
                )
        ).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("AI 포트폴리오 후보는 OPEN + 잔여수량>0 + 모집 기간 내 공모만 endAt asc로 반환한다")
    void findAiPortfolioCandidatesReturnsEligibleOfferingsOrderedByEndAt() {
        UUID soonToEnd = insertOffering("OPEN", "곧종료", 10, 10000, -100, 100, false, UUID.randomUUID());
        UUID laterEnd = insertOffering("OPEN", "나중에종료", 10, 10000, -100, 3_600, false, UUID.randomUUID());
        insertOffering("OPEN", "매진", 0, 10000, -100, 3_600, false, UUID.randomUUID());
        insertOffering("SCHEDULED", "미시작", 10, 10000, 100, 3_600, false, UUID.randomUUID());
        insertOffering("OPEN", "이미종료", 10, 10000, -7_200, -3_600, false, UUID.randomUUID());

        List<AiPortfolioCandidateResponse> candidates =
                offeringQueryRepository.findAiPortfolioCandidates(Instant.now(), 10);

        assertThat(candidates)
                .extracting(AiPortfolioCandidateResponse::offeringId)
                .containsExactly(soonToEnd, laterEnd);
    }

    @Test
    @DisplayName("AI 포트폴리오 후보 조회는 limit 개수만큼만 반환한다")
    void findAiPortfolioCandidatesRespectsLimit() {
        insertOffering("OPEN", "공모1", 10, 10000, -100, 1_000, false, UUID.randomUUID());
        insertOffering("OPEN", "공모2", 10, 10000, -100, 2_000, false, UUID.randomUUID());
        insertOffering("OPEN", "공모3", 10, 10000, -100, 3_000, false, UUID.randomUUID());

        List<AiPortfolioCandidateResponse> candidates =
                offeringQueryRepository.findAiPortfolioCandidates(Instant.now(), 2);

        assertThat(candidates).hasSize(2);
    }
}
