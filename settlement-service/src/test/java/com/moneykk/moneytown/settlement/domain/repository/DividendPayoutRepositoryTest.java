package com.moneykk.moneytown.settlement.domain.repository;

import com.moneykk.moneytown.settlement.domain.entity.DividendPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DividendPayoutRepositoryTest extends RepositoryTestSupport {

    @Autowired
    private SettlementBatchRepository settlementBatchRepository;

    @Autowired
    private DividendPayoutRepository dividendPayoutRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private UUID persistBatch() {
        SettlementBatch batch = SettlementBatch.open(UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 1), 10_000L);
        return settlementBatchRepository.saveAndFlush(batch).getId();
    }

    private DividendPayout persistPayout(UUID settlementBatchId, UUID investorId, Long amount, PayoutStatus status) {
        DividendPayout payout = DividendPayout.queue(settlementBatchId, investorId, BigDecimal.valueOf(0.5), amount);
        applyStatus(payout, status);
        return dividendPayoutRepository.saveAndFlush(payout);
    }

    private void applyStatus(DividendPayout payout, PayoutStatus status) {
        switch (status) {
            case PROCESSING -> payout.markProcessing();
            case PAID -> payout.markPaid();
            case RETRYING -> payout.markRetrying();
            case DEAD_LETTER -> payout.markDeadLetter();
            case QUEUED -> { /* queue() 직후 기본값 */ }
        }
    }

    @Test
    @DisplayName("findByIdAndIsDeletedFalse는 삭제되지 않은 지급 건만 조회한다")
    void findByIdAndIsDeletedFalse_excludesSoftDeleted() {
        UUID batchId = persistBatch();
        DividendPayout payout = persistPayout(batchId, UUID.randomUUID(), 1_000L, PayoutStatus.QUEUED);

        assertThat(dividendPayoutRepository.findByIdAndIsDeletedFalse(payout.getId())).isPresent();

        payout.softDelete(UUID.randomUUID());
        dividendPayoutRepository.saveAndFlush(payout);

        assertThat(dividendPayoutRepository.findByIdAndIsDeletedFalse(payout.getId())).isEmpty();
    }

    @Test
    @DisplayName("findByStatusAndUpdatedAtBeforeAndIsDeletedFalse는 임계 시각 이전에 갱신된 특정 상태 건만 반환한다")
    void findByStatusAndUpdatedAtBefore_returnsOnlyStalePayoutsOfGivenStatus() {
        UUID batchId = persistBatch();
        DividendPayout stale = persistPayout(batchId, UUID.randomUUID(), 1_000L, PayoutStatus.PROCESSING);
        DividendPayout fresh = persistPayout(batchId, UUID.randomUUID(), 1_000L, PayoutStatus.PROCESSING);
        persistPayout(batchId, UUID.randomUUID(), 1_000L, PayoutStatus.PAID);

        // updatedAt은 @LastModifiedDate라 엔티티를 통해 재저장하면 auditing 리스너가 현재 시각으로 덮어써버리므로,
        // JDBC로 직접 컬럼을 갱신하고 영속성 컨텍스트를 비워 DB에 반영된 값을 그대로 읽게 한다.
        Instant threshold = Instant.now();
        jdbcTemplate.update("UPDATE p_dividend_payouts SET updated_at = ? WHERE dividend_payout_id = ?",
                Timestamp.from(threshold.minus(1, ChronoUnit.HOURS)), stale.getId());
        jdbcTemplate.update("UPDATE p_dividend_payouts SET updated_at = ? WHERE dividend_payout_id = ?",
                Timestamp.from(threshold.plus(1, ChronoUnit.HOURS)), fresh.getId());
        entityManager.clear();

        List<DividendPayout> result =
                dividendPayoutRepository.findByStatusAndUpdatedAtBeforeAndIsDeletedFalse(PayoutStatus.PROCESSING, threshold);

        assertThat(result).extracting(DividendPayout::getId).containsExactly(stale.getId());
    }

    @Test
    @DisplayName("findBySettlementBatchIdAndStatusAndIsDeletedFalse는 회차·상태로 지급 건을 필터링한다")
    void findBySettlementBatchIdAndStatus_filtersByBatchAndStatus() {
        UUID batchId = persistBatch();
        DividendPayout queued = persistPayout(batchId, UUID.randomUUID(), 1_000L, PayoutStatus.QUEUED);
        persistPayout(batchId, UUID.randomUUID(), 1_000L, PayoutStatus.PAID);

        List<DividendPayout> result =
                dividendPayoutRepository.findBySettlementBatchIdAndStatusAndIsDeletedFalse(batchId, PayoutStatus.QUEUED);

        assertThat(result).extracting(DividendPayout::getId).containsExactly(queued.getId());
    }

    @Test
    @DisplayName("findBySettlementBatchIdAndStatusInAndIsDeletedFalse는 여러 상태를 한 번에 조회한다")
    void findBySettlementBatchIdAndStatusIn_matchesAnyOfGivenStatuses() {
        UUID batchId = persistBatch();
        DividendPayout retrying = persistPayout(batchId, UUID.randomUUID(), 1_000L, PayoutStatus.RETRYING);
        DividendPayout deadLetter = persistPayout(batchId, UUID.randomUUID(), 1_000L, PayoutStatus.DEAD_LETTER);
        persistPayout(batchId, UUID.randomUUID(), 1_000L, PayoutStatus.PAID);

        List<DividendPayout> result = dividendPayoutRepository.findBySettlementBatchIdAndStatusInAndIsDeletedFalse(
                batchId, List.of(PayoutStatus.RETRYING, PayoutStatus.DEAD_LETTER));

        assertThat(result).extracting(DividendPayout::getId)
                .containsExactlyInAnyOrder(retrying.getId(), deadLetter.getId());
    }

    @Test
    @DisplayName("findBySettlementBatchIdAndIsDeletedFalse(Pageable)는 삭제되지 않은 건만 페이지로 반환한다")
    void findBySettlementBatchIdAndIsDeletedFalse_paged_excludesSoftDeleted() {
        UUID batchId = persistBatch();
        persistPayout(batchId, UUID.randomUUID(), 1_000L, PayoutStatus.QUEUED);
        DividendPayout deleted = persistPayout(batchId, UUID.randomUUID(), 1_000L, PayoutStatus.QUEUED);
        deleted.softDelete(UUID.randomUUID());
        dividendPayoutRepository.saveAndFlush(deleted);

        Page<DividendPayout> page = dividendPayoutRepository
                .findBySettlementBatchIdAndIsDeletedFalse(batchId, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("countByStatusGrouped는 회차 내 지급 건을 상태별 건수로 집계한다")
    void countByStatusGrouped_aggregatesCountsPerStatus() {
        UUID batchId = persistBatch();
        persistPayout(batchId, UUID.randomUUID(), 1_000L, PayoutStatus.PAID);
        persistPayout(batchId, UUID.randomUUID(), 1_000L, PayoutStatus.PAID);
        persistPayout(batchId, UUID.randomUUID(), 1_000L, PayoutStatus.DEAD_LETTER);

        Map<PayoutStatus, Long> counts = dividendPayoutRepository.countByStatusGrouped(batchId).stream()
                .collect(Collectors.toMap(DividendPayoutRepository.PayoutStatusCount::getStatus,
                        DividendPayoutRepository.PayoutStatusCount::getCount));

        assertThat(counts.get(PayoutStatus.PAID)).isEqualTo(2L);
        assertThat(counts.get(PayoutStatus.DEAD_LETTER)).isEqualTo(1L);
    }

    @Test
    @DisplayName("findDistinctSettlementBatchIdByStatusIn은 대상 상태를 가진 지급 건의 회차 ID를 중복 없이 반환한다")
    void findDistinctSettlementBatchIdByStatusIn_returnsUniqueBatchIds() {
        UUID batchId = persistBatch();
        persistPayout(batchId, UUID.randomUUID(), 1_000L, PayoutStatus.QUEUED);
        persistPayout(batchId, UUID.randomUUID(), 1_000L, PayoutStatus.QUEUED);

        List<UUID> result = dividendPayoutRepository.findDistinctSettlementBatchIdByStatusIn(List.of(PayoutStatus.QUEUED));

        assertThat(result).containsExactly(batchId);
    }

    @Test
    @DisplayName("findMyDividendPayouts는 투자자 본인의 배당 내역을 자산 필터 없이 조회한다")
    void findMyDividendPayouts_withoutAssetFilter_returnsAllInvestorPayouts() {
        UUID investorId = UUID.randomUUID();
        UUID batchId = persistBatch();
        SettlementBatch batch = settlementBatchRepository.findByIdAndIsDeletedFalse(batchId).orElseThrow();
        persistPayout(batchId, investorId, 1_000L, PayoutStatus.PAID);
        persistPayout(batchId, UUID.randomUUID(), 2_000L, PayoutStatus.PAID);

        Page<DividendPayoutRepository.MyDividendPayoutRow> page =
                dividendPayoutRepository.findMyDividendPayouts(investorId, null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        DividendPayoutRepository.MyDividendPayoutRow row = page.getContent().get(0);
        assertThat(row.getAssetId()).isEqualTo(batch.getAssetId());
        assertThat(row.getAmount()).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("findMyDividendPayouts는 assetId를 지정하면 해당 자산 건으로만 필터링한다")
    void findMyDividendPayouts_withAssetFilter_filtersByAsset() {
        UUID investorId = UUID.randomUUID();
        UUID matchingBatchId = persistBatch();
        UUID matchingAssetId = settlementBatchRepository.findByIdAndIsDeletedFalse(matchingBatchId).orElseThrow().getAssetId();
        UUID otherBatchId = persistBatch();
        persistPayout(matchingBatchId, investorId, 1_000L, PayoutStatus.QUEUED);
        persistPayout(otherBatchId, investorId, 2_000L, PayoutStatus.QUEUED);

        Page<DividendPayoutRepository.MyDividendPayoutRow> page =
                dividendPayoutRepository.findMyDividendPayouts(investorId, matchingAssetId, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getSettlementBatchId()).isEqualTo(matchingBatchId);
    }

    @Test
    @DisplayName("같은 회차에 같은 투자자로 지급 건을 두 번 저장하면 유니크 제약 위반으로 실패한다")
    void savingDuplicatePayoutForSameBatchAndInvestor_violatesUniqueConstraint() {
        UUID batchId = persistBatch();
        UUID investorId = UUID.randomUUID();
        persistPayout(batchId, investorId, 1_000L, PayoutStatus.QUEUED);

        DividendPayout duplicate = DividendPayout.queue(batchId, investorId, BigDecimal.valueOf(0.5), 2_000L);

        Optional<DividendPayout> saved = trySave(duplicate);
        assertThat(saved).isEmpty();
    }

    private Optional<DividendPayout> trySave(DividendPayout payout) {
        try {
            return Optional.of(dividendPayoutRepository.saveAndFlush(payout));
        } catch (DataIntegrityViolationException e) {
            return Optional.empty();
        }
    }
}