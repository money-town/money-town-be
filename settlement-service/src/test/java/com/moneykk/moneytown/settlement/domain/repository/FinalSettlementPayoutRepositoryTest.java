package com.moneykk.moneytown.settlement.domain.repository;

import com.moneykk.moneytown.settlement.domain.entity.DeadLetterReason;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinalSettlementPayoutRepositoryTest extends RepositoryTestSupport {

    @Autowired
    private FinalSettlementBatchRepository finalSettlementBatchRepository;

    @Autowired
    private FinalSettlementPayoutRepository finalSettlementPayoutRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private UUID persistBatch() {
        FinalSettlementBatch batch = FinalSettlementBatch.open(
                UUID.randomUUID(), Instant.parse("2026-09-01T00:00:00Z"), 1_000L, 100_000L);
        return finalSettlementBatchRepository.saveAndFlush(batch).getId();
    }

    private FinalSettlementPayout persistPayout(UUID batchId, UUID investorId, Long amount, PayoutStatus status) {
        FinalSettlementPayout payout = FinalSettlementPayout.queue(batchId, investorId, 10L, amount);
        applyStatus(payout, status);
        return finalSettlementPayoutRepository.saveAndFlush(payout);
    }

    private void applyStatus(FinalSettlementPayout payout, PayoutStatus status) {
        switch (status) {
            case PROCESSING -> payout.markProcessing();
            case PAID -> payout.markPaid();
            case RETRYING -> payout.markRetrying();
            case DEAD_LETTER -> payout.markDeadLetter(DeadLetterReason.RETRY_EXCEEDED);
            case QUEUED -> { /* queue() 직후 기본값 */ }
        }
    }

    @Test
    @DisplayName("findByIdAndIsDeletedFalse는 삭제되지 않은 반환 건만 조회한다")
    void findByIdAndIsDeletedFalse_excludesSoftDeleted() {
        UUID batchId = persistBatch();
        FinalSettlementPayout payout = persistPayout(batchId, UUID.randomUUID(), 10_000L, PayoutStatus.QUEUED);

        assertThat(finalSettlementPayoutRepository.findByIdAndIsDeletedFalse(payout.getId())).isPresent();

        payout.softDelete(UUID.randomUUID());
        finalSettlementPayoutRepository.saveAndFlush(payout);

        assertThat(finalSettlementPayoutRepository.findByIdAndIsDeletedFalse(payout.getId())).isEmpty();
    }

    @Test
    @DisplayName("findByStatusAndUpdatedAtBeforeAndIsDeletedFalse는 임계 시각 이전에 갱신된 특정 상태 건만 반환한다")
    void findByStatusAndUpdatedAtBefore_returnsOnlyStalePayoutsOfGivenStatus() {
        UUID batchId = persistBatch();
        FinalSettlementPayout stale = persistPayout(batchId, UUID.randomUUID(), 10_000L, PayoutStatus.PROCESSING);
        FinalSettlementPayout fresh = persistPayout(batchId, UUID.randomUUID(), 10_000L, PayoutStatus.PROCESSING);

        // updatedAt은 @LastModifiedDate라 엔티티 재저장으로는 값을 고정할 수 없어 JDBC로 직접 갱신한다.
        Instant threshold = Instant.now();
        jdbcTemplate.update("UPDATE p_final_settlement_payouts SET updated_at = ? WHERE final_settlement_payout_id = ?",
                Timestamp.from(threshold.minus(1, ChronoUnit.HOURS)), stale.getId());
        jdbcTemplate.update("UPDATE p_final_settlement_payouts SET updated_at = ? WHERE final_settlement_payout_id = ?",
                Timestamp.from(threshold.plus(1, ChronoUnit.HOURS)), fresh.getId());
        entityManager.clear();

        List<FinalSettlementPayout> result = finalSettlementPayoutRepository
                .findByStatusAndUpdatedAtBeforeAndIsDeletedFalse(PayoutStatus.PROCESSING, threshold);

        assertThat(result).extracting(FinalSettlementPayout::getId).containsExactly(stale.getId());
    }

    @Test
    @DisplayName("findDistinctFinalSettlementBatchIdByStatusIn은 대상 상태를 가진 반환 건의 회차 ID를 중복 없이 반환한다")
    void findDistinctFinalSettlementBatchIdByStatusIn_returnsUniqueBatchIds() {
        UUID batchId = persistBatch();
        persistPayout(batchId, UUID.randomUUID(), 10_000L, PayoutStatus.QUEUED);
        persistPayout(batchId, UUID.randomUUID(), 10_000L, PayoutStatus.QUEUED);

        List<UUID> result =
                finalSettlementPayoutRepository.findDistinctFinalSettlementBatchIdByStatusIn(List.of(PayoutStatus.QUEUED));

        assertThat(result).containsExactly(batchId);
    }

    @Test
    @DisplayName("findByFinalSettlementBatchIdAndStatusInAndIsDeletedFalse는 여러 상태를 한 번에 조회한다")
    void findByFinalSettlementBatchIdAndStatusIn_matchesAnyOfGivenStatuses() {
        UUID batchId = persistBatch();
        FinalSettlementPayout retrying = persistPayout(batchId, UUID.randomUUID(), 10_000L, PayoutStatus.RETRYING);
        FinalSettlementPayout deadLetter = persistPayout(batchId, UUID.randomUUID(), 10_000L, PayoutStatus.DEAD_LETTER);
        persistPayout(batchId, UUID.randomUUID(), 10_000L, PayoutStatus.PAID);

        List<FinalSettlementPayout> result = finalSettlementPayoutRepository
                .findByFinalSettlementBatchIdAndStatusInAndIsDeletedFalse(
                        batchId, List.of(PayoutStatus.RETRYING, PayoutStatus.DEAD_LETTER));

        assertThat(result).extracting(FinalSettlementPayout::getId)
                .containsExactlyInAnyOrder(retrying.getId(), deadLetter.getId());
    }

    @Test
    @DisplayName("findByFinalSettlementBatchIdAndIsDeletedFalse는 회차의 삭제되지 않은 반환 건 전체를 반환한다")
    void findByFinalSettlementBatchIdAndIsDeletedFalse_excludesSoftDeleted() {
        UUID batchId = persistBatch();
        FinalSettlementPayout kept = persistPayout(batchId, UUID.randomUUID(), 10_000L, PayoutStatus.QUEUED);
        FinalSettlementPayout deleted = persistPayout(batchId, UUID.randomUUID(), 10_000L, PayoutStatus.QUEUED);
        deleted.softDelete(UUID.randomUUID());
        finalSettlementPayoutRepository.saveAndFlush(deleted);

        List<FinalSettlementPayout> result = finalSettlementPayoutRepository.findByFinalSettlementBatchIdAndIsDeletedFalse(batchId);

        assertThat(result).extracting(FinalSettlementPayout::getId).containsExactly(kept.getId());
    }

    @Test
    @DisplayName("findByFinalSettlementBatchIdAndStatusAndIsDeletedFalse는 회차·상태로 반환 건을 필터링한다")
    void findByFinalSettlementBatchIdAndStatus_filtersByBatchAndStatus() {
        UUID batchId = persistBatch();
        FinalSettlementPayout deadLetter = persistPayout(batchId, UUID.randomUUID(), 10_000L, PayoutStatus.DEAD_LETTER);
        persistPayout(batchId, UUID.randomUUID(), 10_000L, PayoutStatus.QUEUED);

        List<FinalSettlementPayout> result = finalSettlementPayoutRepository
                .findByFinalSettlementBatchIdAndStatusAndIsDeletedFalse(batchId, PayoutStatus.DEAD_LETTER);

        assertThat(result).extracting(FinalSettlementPayout::getId).containsExactly(deadLetter.getId());
    }

    @Test
    @DisplayName("findByFinalSettlementBatchIdAndIdInAndStatusAndIsDeletedFalse는 지정한 ID 목록 중 해당 상태인 건만 반환한다")
    void findByFinalSettlementBatchIdAndIdInAndStatus_filtersBySelectedIdsAndStatus() {
        UUID batchId = persistBatch();
        FinalSettlementPayout selectedDeadLetter = persistPayout(batchId, UUID.randomUUID(), 10_000L, PayoutStatus.DEAD_LETTER);
        FinalSettlementPayout unselectedDeadLetter = persistPayout(batchId, UUID.randomUUID(), 10_000L, PayoutStatus.DEAD_LETTER);
        FinalSettlementPayout selectedButNotDeadLetter = persistPayout(batchId, UUID.randomUUID(), 10_000L, PayoutStatus.QUEUED);

        List<FinalSettlementPayout> result = finalSettlementPayoutRepository
                .findByFinalSettlementBatchIdAndIdInAndStatusAndIsDeletedFalse(
                        batchId,
                        List.of(selectedDeadLetter.getId(), selectedButNotDeadLetter.getId()),
                        PayoutStatus.DEAD_LETTER);

        assertThat(result).extracting(FinalSettlementPayout::getId).containsExactly(selectedDeadLetter.getId());
        assertThat(result).extracting(FinalSettlementPayout::getId).doesNotContain(unselectedDeadLetter.getId());
    }

    @Test
    @DisplayName("findByFinalSettlementBatchIdAndIsDeletedFalse(Pageable)는 삭제되지 않은 건만 페이지로 반환한다")
    void findByFinalSettlementBatchIdAndIsDeletedFalse_paged_excludesSoftDeleted() {
        UUID batchId = persistBatch();
        persistPayout(batchId, UUID.randomUUID(), 10_000L, PayoutStatus.QUEUED);
        FinalSettlementPayout deleted = persistPayout(batchId, UUID.randomUUID(), 10_000L, PayoutStatus.QUEUED);
        deleted.softDelete(UUID.randomUUID());
        finalSettlementPayoutRepository.saveAndFlush(deleted);

        Page<FinalSettlementPayout> page = finalSettlementPayoutRepository
                .findByFinalSettlementBatchIdAndIsDeletedFalse(batchId, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("findByFinalSettlementBatchIdAndStatusAndIsDeletedFalse(Pageable)는 회차·상태로 페이지를 필터링한다")
    void findByFinalSettlementBatchIdAndStatus_paged_filtersByStatus() {
        UUID batchId = persistBatch();
        FinalSettlementPayout paid = persistPayout(batchId, UUID.randomUUID(), 10_000L, PayoutStatus.PAID);
        persistPayout(batchId, UUID.randomUUID(), 10_000L, PayoutStatus.QUEUED);

        Page<FinalSettlementPayout> page = finalSettlementPayoutRepository
                .findByFinalSettlementBatchIdAndStatusAndIsDeletedFalse(batchId, PayoutStatus.PAID, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(FinalSettlementPayout::getId).containsExactly(paid.getId());
    }

    @Test
    @DisplayName("countByStatusGrouped는 회차 내 반환 건을 상태별 건수로 집계한다")
    void countByStatusGrouped_aggregatesCountsPerStatus() {
        UUID batchId = persistBatch();
        persistPayout(batchId, UUID.randomUUID(), 10_000L, PayoutStatus.PAID);
        persistPayout(batchId, UUID.randomUUID(), 10_000L, PayoutStatus.PAID);
        persistPayout(batchId, UUID.randomUUID(), 10_000L, PayoutStatus.DEAD_LETTER);

        Map<PayoutStatus, Long> counts = finalSettlementPayoutRepository.countByStatusGrouped(batchId).stream()
                .collect(Collectors.toMap(FinalSettlementPayoutRepository.PayoutStatusCount::getStatus,
                        FinalSettlementPayoutRepository.PayoutStatusCount::getCount));

        assertThat(counts.get(PayoutStatus.PAID)).isEqualTo(2L);
        assertThat(counts.get(PayoutStatus.DEAD_LETTER)).isEqualTo(1L);
    }

    @Test
    @DisplayName("같은 회차에 같은 투자자로 반환 건을 두 번 저장하면 유니크 제약 위반으로 실패한다")
    void savingDuplicatePayoutForSameBatchAndInvestor_violatesUniqueConstraint() {
        UUID batchId = persistBatch();
        UUID investorId = UUID.randomUUID();
        persistPayout(batchId, investorId, 10_000L, PayoutStatus.QUEUED);

        FinalSettlementPayout duplicate = FinalSettlementPayout.queue(batchId, investorId, 10L, 20_000L);

        assertThatThrownBy(() -> finalSettlementPayoutRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}