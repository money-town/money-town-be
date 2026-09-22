package com.moneykk.moneytown.settlement.domain.repository;

import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementBatchRepositoryTest extends RepositoryTestSupport {

    @Autowired
    private SettlementBatchRepository settlementBatchRepository;

    private SettlementBatch persistBatch(UUID assetId, UUID revenueId, SettlementStatus status) {
        SettlementBatch batch = SettlementBatch.open(assetId, revenueId, LocalDate.of(2026, 9, 1), 10_000L);
        applyStatus(batch, status);
        return settlementBatchRepository.saveAndFlush(batch);
    }

    private void applyStatus(SettlementBatch batch, SettlementStatus status) {
        switch (status) {
            case SNAPSHOT_TAKEN -> batch.markSnapshotTaken();
            case CALCULATED -> batch.markCalculated();
            case DISBURSING -> batch.markDisbursing();
            case COMPLETED -> batch.markCompleted();
            case PARTIAL_FAILED -> batch.markPartialFailed();
            case FAILED -> batch.markFailed();
            case CLOSED_ABANDONED -> batch.markClosedAbandoned();
            case PENDING -> { /* open() 직후 기본값 */ }
        }
    }

    @Test
    @DisplayName("같은 revenueId로 저장된 회차를 조회한다")
    void existsByRevenueId_returnsTrue_whenBatchExistsForRevenue() {
        UUID revenueId = UUID.randomUUID();
        persistBatch(UUID.randomUUID(), revenueId, SettlementStatus.PENDING);

        assertThat(settlementBatchRepository.findByRevenueIdAndIsDeletedFalse(revenueId)).isPresent();
        assertThat(settlementBatchRepository.findByRevenueIdAndIsDeletedFalse(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("Soft Delete된 회차는 revenueId 조회에서 제외된다")
    void existsByRevenueId_excludesSoftDeletedBatch() {
        UUID revenueId = UUID.randomUUID();
        SettlementBatch batch = persistBatch(UUID.randomUUID(), revenueId, SettlementStatus.PENDING);
        batch.softDelete(UUID.randomUUID());
        settlementBatchRepository.saveAndFlush(batch);

        assertThat(settlementBatchRepository.findByRevenueIdAndIsDeletedFalse(revenueId)).isEmpty();
    }

    @Test
    @DisplayName("자산에 종결 상태가 아닌 진행 중 회차가 있으면 existsByAssetIdAndStatusNotInAndIsDeletedFalse가 true를 반환한다")
    void existsByAssetIdAndStatusNotIn_returnsTrue_whenBatchInProgressForAsset() {
        UUID assetId = UUID.randomUUID();
        persistBatch(assetId, UUID.randomUUID(), SettlementStatus.CALCULATED);

        assertThat(settlementBatchRepository.existsByAssetIdAndStatusNotInAndIsDeletedFalse(
                assetId, List.of(SettlementStatus.COMPLETED, SettlementStatus.CLOSED_ABANDONED))).isTrue();
    }

    @Test
    @DisplayName("자산의 회차가 종결 상태(COMPLETED/CLOSED_ABANDONED)면 existsByAssetIdAndStatusNotInAndIsDeletedFalse는 false를 반환한다")
    void existsByAssetIdAndStatusNotIn_returnsFalse_whenBatchTerminal() {
        UUID completedAssetId = UUID.randomUUID();
        persistBatch(completedAssetId, UUID.randomUUID(), SettlementStatus.COMPLETED);

        UUID closedAbandonedAssetId = UUID.randomUUID();
        SettlementBatch closedAbandoned = SettlementBatch.open(closedAbandonedAssetId, UUID.randomUUID(), LocalDate.of(2026, 9, 1), 10_000L);
        closedAbandoned.markClosedAbandoned();
        settlementBatchRepository.saveAndFlush(closedAbandoned);

        List<SettlementStatus> terminalStatuses = List.of(SettlementStatus.COMPLETED, SettlementStatus.CLOSED_ABANDONED);
        assertThat(settlementBatchRepository
                .existsByAssetIdAndStatusNotInAndIsDeletedFalse(completedAssetId, terminalStatuses)).isFalse();
        assertThat(settlementBatchRepository
                .existsByAssetIdAndStatusNotInAndIsDeletedFalse(closedAbandonedAssetId, terminalStatuses)).isFalse();
    }

    @Test
    @DisplayName("findByIdAndIsDeletedFalse는 삭제되지 않은 회차만 조회한다")
    void findByIdAndIsDeletedFalse_returnsBatch_whenNotDeleted() {
        SettlementBatch batch = persistBatch(UUID.randomUUID(), UUID.randomUUID(), SettlementStatus.PENDING);

        Optional<SettlementBatch> found = settlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(batch.getId());
    }

    @Test
    @DisplayName("findByIdAndIsDeletedFalse는 Soft Delete된 회차에 대해 empty를 반환한다")
    void findByIdAndIsDeletedFalse_returnsEmpty_whenSoftDeleted() {
        SettlementBatch batch = persistBatch(UUID.randomUUID(), UUID.randomUUID(), SettlementStatus.PENDING);
        batch.softDelete(UUID.randomUUID());
        settlementBatchRepository.saveAndFlush(batch);

        assertThat(settlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId())).isEmpty();
    }

    @Test
    @DisplayName("existsByIdAndIsDeletedFalse는 존재 여부를 삭제 상태와 함께 확인한다")
    void existsByIdAndIsDeletedFalse_reflectsSoftDeleteState() {
        SettlementBatch batch = persistBatch(UUID.randomUUID(), UUID.randomUUID(), SettlementStatus.PENDING);
        assertThat(settlementBatchRepository.existsByIdAndIsDeletedFalse(batch.getId())).isTrue();

        batch.softDelete(UUID.randomUUID());
        settlementBatchRepository.saveAndFlush(batch);
        assertThat(settlementBatchRepository.existsByIdAndIsDeletedFalse(batch.getId())).isFalse();
    }

    @Test
    @DisplayName("findFirstByAssetIdAndStatusIn은 해당 자산의 FAILED/PARTIAL_FAILED 회차만 반환한다 (T5)")
    void findFirstByAssetIdAndStatusIn_returnsOnlyFailedBatchOfSameAsset() {
        UUID assetId = UUID.randomUUID();
        SettlementBatch failed = persistBatch(assetId, UUID.randomUUID(), SettlementStatus.PARTIAL_FAILED);
        persistBatch(UUID.randomUUID(), UUID.randomUUID(), SettlementStatus.FAILED);
        UUID disbursingAssetId = UUID.randomUUID();
        persistBatch(disbursingAssetId, UUID.randomUUID(), SettlementStatus.DISBURSING);

        List<SettlementStatus> failureStatuses = List.of(SettlementStatus.FAILED, SettlementStatus.PARTIAL_FAILED);

        assertThat(settlementBatchRepository.findFirstByAssetIdAndStatusInAndIsDeletedFalse(assetId, failureStatuses))
                .get().extracting(SettlementBatch::getId).isEqualTo(failed.getId());
        assertThat(settlementBatchRepository.findFirstByAssetIdAndStatusInAndIsDeletedFalse(disbursingAssetId, failureStatuses))
                .isEmpty();
    }

    @Test
    @DisplayName("findByStatusIn은 FAILED/PARTIAL_FAILED만 반환하고 마감(COMPLETED/CLOSED_ABANDONED)·진행 중·삭제된 회차는 제외한다 — 마감되면 재통보가 멈춘다 (T6)")
    void findByStatusIn_returnsOnlyUnresolvedFailures() {
        SettlementBatch failed = persistBatch(UUID.randomUUID(), UUID.randomUUID(), SettlementStatus.FAILED);
        SettlementBatch partial = persistBatch(UUID.randomUUID(), UUID.randomUUID(), SettlementStatus.PARTIAL_FAILED);
        persistBatch(UUID.randomUUID(), UUID.randomUUID(), SettlementStatus.CLOSED_ABANDONED);
        persistBatch(UUID.randomUUID(), UUID.randomUUID(), SettlementStatus.COMPLETED);
        persistBatch(UUID.randomUUID(), UUID.randomUUID(), SettlementStatus.DISBURSING);
        SettlementBatch deleted = persistBatch(UUID.randomUUID(), UUID.randomUUID(), SettlementStatus.FAILED);
        deleted.softDelete(UUID.randomUUID());
        settlementBatchRepository.saveAndFlush(deleted);

        List<SettlementBatch> result = settlementBatchRepository.findByStatusInAndIsDeletedFalse(
                List.of(SettlementStatus.FAILED, SettlementStatus.PARTIAL_FAILED));

        assertThat(result).extracting(SettlementBatch::getId).containsExactlyInAnyOrder(failed.getId(), partial.getId());
    }

    @Test
    @DisplayName("CLOSED_ABANDONED로 마감된 회차가 있어도 같은 자산의 새 진행 중 회차를 저장할 수 있다 (V17 부분 유니크 인덱스)")
    void closedAbandonedBatch_doesNotBlockNewBatchForSameAsset() {
        UUID assetId = UUID.randomUUID();
        persistBatch(assetId, UUID.randomUUID(), SettlementStatus.CLOSED_ABANDONED);

        SettlementBatch next = persistBatch(assetId, UUID.randomUUID(), SettlementStatus.PENDING);

        assertThat(settlementBatchRepository.findByIdAndIsDeletedFalse(next.getId())).isPresent();
    }

    @Test
    @DisplayName("PARTIAL_FAILED 회차가 있으면 같은 자산의 새 회차는 여전히 유니크 인덱스에 막힌다 (T5 차단 조건 유지)")
    void partialFailedBatch_stillBlocksNewBatchForSameAsset() {
        UUID assetId = UUID.randomUUID();
        persistBatch(assetId, UUID.randomUUID(), SettlementStatus.PARTIAL_FAILED);

        SettlementBatch duplicate = SettlementBatch.open(assetId, UUID.randomUUID(), LocalDate.of(2026, 9, 1), 5_000L);

        assertThatThrownBy(() -> settlementBatchRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 자산에 진행 중(COMPLETED 아님) 회차를 두 번 저장하면 부분 유니크 인덱스 위반으로 실패한다")
    void savingSecondInProgressBatchForSameAsset_violatesPartialUniqueIndex() {
        UUID assetId = UUID.randomUUID();
        persistBatch(assetId, UUID.randomUUID(), SettlementStatus.PENDING);

        SettlementBatch duplicate = SettlementBatch.open(assetId, UUID.randomUUID(), LocalDate.of(2026, 9, 1), 5_000L);

        assertThatThrownBy(() -> settlementBatchRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
