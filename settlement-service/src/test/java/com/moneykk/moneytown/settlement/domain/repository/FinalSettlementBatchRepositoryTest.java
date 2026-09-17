package com.moneykk.moneytown.settlement.domain.repository;

import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinalSettlementBatchRepositoryTest extends RepositoryTestSupport {

    @Autowired
    private FinalSettlementBatchRepository finalSettlementBatchRepository;

    private FinalSettlementBatch persistBatch(UUID assetId, SettlementStatus status) {
        FinalSettlementBatch batch = FinalSettlementBatch.open(assetId, Instant.parse("2026-09-01T00:00:00Z"), 1_000L, 100_000L);
        applyStatus(batch, status);
        return finalSettlementBatchRepository.saveAndFlush(batch);
    }

    private void applyStatus(FinalSettlementBatch batch, SettlementStatus status) {
        switch (status) {
            case CALCULATED -> batch.markCalculated();
            case DISBURSING -> batch.markDisbursing();
            case COMPLETED -> batch.markCompleted();
            case PARTIAL_FAILED -> batch.markPartialFailed();
            case FAILED -> batch.markFailed();
            default -> { /* PENDING/SNAPSHOT_TAKEN은 open() 직후 기본값으로 충분 */ }
        }
    }

    @Test
    @DisplayName("findByAssetIdAndIsDeletedFalse는 자산으로 삭제되지 않은 회차를 조회한다")
    void findByAssetIdAndIsDeletedFalse_returnsBatch_whenNotDeleted() {
        UUID assetId = UUID.randomUUID();
        FinalSettlementBatch batch = persistBatch(assetId, SettlementStatus.CALCULATED);

        Optional<FinalSettlementBatch> found = finalSettlementBatchRepository.findByAssetIdAndIsDeletedFalse(assetId);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(batch.getId());
    }

    @Test
    @DisplayName("자산당 최종 정산 회차는 하나만 허용되어(유니크 제약) 두 번째 생성 시 실패한다")
    void savingSecondBatchForSameAsset_violatesUniqueConstraint() {
        UUID assetId = UUID.randomUUID();
        persistBatch(assetId, SettlementStatus.CALCULATED);

        FinalSettlementBatch duplicate = FinalSettlementBatch.open(assetId, Instant.parse("2026-09-02T00:00:00Z"), 1_000L, 50_000L);

        assertThatThrownBy(() -> finalSettlementBatchRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("findByIdAndIsDeletedFalse는 Soft Delete된 회차에 대해 empty를 반환한다")
    void findByIdAndIsDeletedFalse_returnsEmpty_whenSoftDeleted() {
        FinalSettlementBatch batch = persistBatch(UUID.randomUUID(), SettlementStatus.CALCULATED);
        batch.softDelete(UUID.randomUUID());
        finalSettlementBatchRepository.saveAndFlush(batch);

        assertThat(finalSettlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId())).isEmpty();
    }

    @Test
    @DisplayName("existsByIdAndIsDeletedFalse는 존재 여부를 삭제 상태와 함께 확인한다")
    void existsByIdAndIsDeletedFalse_reflectsSoftDeleteState() {
        FinalSettlementBatch batch = persistBatch(UUID.randomUUID(), SettlementStatus.CALCULATED);
        assertThat(finalSettlementBatchRepository.existsByIdAndIsDeletedFalse(batch.getId())).isTrue();

        batch.softDelete(UUID.randomUUID());
        finalSettlementBatchRepository.saveAndFlush(batch);
        assertThat(finalSettlementBatchRepository.existsByIdAndIsDeletedFalse(batch.getId())).isFalse();
    }

    @Test
    @DisplayName("findByStatusAndAssetTerminationCompletedAtIsNullAndIsDeletedFalse는 자산 종료 통보가 안 된 COMPLETED 회차만 반환한다")
    void findByStatusAndAssetTerminationCompletedAtIsNull_returnsOnlyUnnotifiedCompletedBatches() {
        FinalSettlementBatch notified = persistBatch(UUID.randomUUID(), SettlementStatus.COMPLETED);
        notified.markAssetTerminationCompleted(Instant.now());
        finalSettlementBatchRepository.saveAndFlush(notified);

        FinalSettlementBatch unnotified = persistBatch(UUID.randomUUID(), SettlementStatus.COMPLETED);

        persistBatch(UUID.randomUUID(), SettlementStatus.DISBURSING);

        List<FinalSettlementBatch> result = finalSettlementBatchRepository
                .findByStatusAndAssetTerminationCompletedAtIsNullAndIsDeletedFalse(SettlementStatus.COMPLETED);

        assertThat(result).extracting(FinalSettlementBatch::getId).containsExactly(unnotified.getId());
    }
}