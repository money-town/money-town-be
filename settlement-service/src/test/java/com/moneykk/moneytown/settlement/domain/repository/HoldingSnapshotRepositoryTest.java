package com.moneykk.moneytown.settlement.domain.repository;

import com.moneykk.moneytown.settlement.domain.entity.HoldingSnapshot;
import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HoldingSnapshotRepositoryTest extends RepositoryTestSupport {

    @Autowired
    private SettlementBatchRepository settlementBatchRepository;

    @Autowired
    private HoldingSnapshotRepository holdingSnapshotRepository;

    private UUID persistBatch() {
        SettlementBatch batch = SettlementBatch.open(UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 1), 10_000L);
        return settlementBatchRepository.saveAndFlush(batch).getId();
    }

    @Test
    @DisplayName("스냅샷을 저장하면 지정한 필드 그대로 조회된다")
    void savesAndReturnsSnapshotWithGivenFields() {
        UUID batchId = persistBatch();
        UUID assetId = UUID.randomUUID();
        HoldingSnapshot snapshot = HoldingSnapshot.capture(batchId, assetId, LocalDate.of(2026, 9, 1), 1_000L, 5, 1_000L);

        HoldingSnapshot saved = holdingSnapshotRepository.saveAndFlush(snapshot);

        Optional<HoldingSnapshot> found = holdingSnapshotRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getSettlementBatchId()).isEqualTo(batchId);
        assertThat(found.get().getAssetId()).isEqualTo(assetId);
        assertThat(found.get().getTotalQuantity()).isEqualTo(1_000L);
        assertThat(found.get().getTotalHolders()).isEqualTo(5);
        assertThat(found.get().getTotalShareQuantity()).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("같은 정산 회차에 스냅샷을 두 번 저장하면 유니크 제약 위반으로 실패한다")
    void savingSecondSnapshotForSameBatch_violatesUniqueConstraint() {
        UUID batchId = persistBatch();
        holdingSnapshotRepository.saveAndFlush(
                HoldingSnapshot.capture(batchId, UUID.randomUUID(), LocalDate.of(2026, 9, 1), 1_000L, 5, 1_000L));

        HoldingSnapshot duplicate =
                HoldingSnapshot.capture(batchId, UUID.randomUUID(), LocalDate.of(2026, 9, 1), 2_000L, 3, 2_000L);

        assertThatThrownBy(() -> holdingSnapshotRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}