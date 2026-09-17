package com.moneykk.moneytown.settlement.domain.repository;

import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
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
            case PENDING -> { /* open() 직후 기본값 */ }
        }
    }

    @Test
    @DisplayName("같은 revenueId로 저장된 회차가 있으면 existsByRevenueIdAndIsDeletedFalse가 true를 반환한다")
    void existsByRevenueId_returnsTrue_whenBatchExistsForRevenue() {
        UUID revenueId = UUID.randomUUID();
        persistBatch(UUID.randomUUID(), revenueId, SettlementStatus.PENDING);

        assertThat(settlementBatchRepository.existsByRevenueIdAndIsDeletedFalse(revenueId)).isTrue();
        assertThat(settlementBatchRepository.existsByRevenueIdAndIsDeletedFalse(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("Soft Delete된 회차는 existsByRevenueIdAndIsDeletedFalse에서 제외된다")
    void existsByRevenueId_excludesSoftDeletedBatch() {
        UUID revenueId = UUID.randomUUID();
        SettlementBatch batch = persistBatch(UUID.randomUUID(), revenueId, SettlementStatus.PENDING);
        batch.softDelete(UUID.randomUUID());
        settlementBatchRepository.saveAndFlush(batch);

        assertThat(settlementBatchRepository.existsByRevenueIdAndIsDeletedFalse(revenueId)).isFalse();
    }

    @Test
    @DisplayName("자산에 COMPLETED가 아닌 진행 중 회차가 있으면 existsByAssetIdAndStatusNotAndIsDeletedFalse가 true를 반환한다")
    void existsByAssetIdAndStatusNot_returnsTrue_whenBatchInProgressForAsset() {
        UUID assetId = UUID.randomUUID();
        persistBatch(assetId, UUID.randomUUID(), SettlementStatus.CALCULATED);

        assertThat(settlementBatchRepository
                .existsByAssetIdAndStatusNotAndIsDeletedFalse(assetId, SettlementStatus.COMPLETED)).isTrue();
    }

    @Test
    @DisplayName("자산의 회차가 COMPLETED 상태면 existsByAssetIdAndStatusNotAndIsDeletedFalse는 false를 반환한다")
    void existsByAssetIdAndStatusNot_returnsFalse_whenBatchCompleted() {
        UUID assetId = UUID.randomUUID();
        persistBatch(assetId, UUID.randomUUID(), SettlementStatus.COMPLETED);

        assertThat(settlementBatchRepository
                .existsByAssetIdAndStatusNotAndIsDeletedFalse(assetId, SettlementStatus.COMPLETED)).isFalse();
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
    @DisplayName("같은 자산에 진행 중(COMPLETED 아님) 회차를 두 번 저장하면 부분 유니크 인덱스 위반으로 실패한다")
    void savingSecondInProgressBatchForSameAsset_violatesPartialUniqueIndex() {
        UUID assetId = UUID.randomUUID();
        persistBatch(assetId, UUID.randomUUID(), SettlementStatus.PENDING);

        SettlementBatch duplicate = SettlementBatch.open(assetId, UUID.randomUUID(), LocalDate.of(2026, 9, 1), 5_000L);

        assertThatThrownBy(() -> settlementBatchRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}