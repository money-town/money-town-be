package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.settlement.domain.entity.DividendPayout;
import com.moneykk.moneytown.settlement.domain.entity.HoldingSnapshot;
import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.domain.repository.DividendPayoutRepository;
import com.moneykk.moneytown.settlement.domain.repository.HoldingSnapshotRepository;
import com.moneykk.moneytown.settlement.domain.repository.SettlementBatchRepository;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementBatchWriterTest {

    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final UUID REVENUE_ID = UUID.randomUUID();
    private static final LocalDate RECORD_DATE = LocalDate.of(2026, 9, 1);

    @Mock
    private SettlementBatchRepository settlementBatchRepository;
    @Mock
    private HoldingSnapshotRepository holdingSnapshotRepository;
    @Mock
    private DividendPayoutRepository dividendPayoutRepository;

    @InjectMocks
    private SettlementBatchWriter settlementBatchWriter;

    @Test
    @DisplayName("배치·스냅샷·payout을 순서대로 저장한다")
    void persistsBatchSnapshotAndPayouts() {
        SettlementBatch batch = batch();
        HoldingSnapshot snapshot = HoldingSnapshot.capture(batch.getId(), ASSET_ID, RECORD_DATE, 1L, 1, 1L);
        List<DividendPayout> payouts = List.of(DividendPayout.queue(batch.getId(), UUID.randomUUID(), BigDecimal.ONE, 1_000_000L));

        settlementBatchWriter.persist(batch, snapshot, payouts);

        verify(settlementBatchRepository).saveAndFlush(batch);
        verify(holdingSnapshotRepository).save(snapshot);
        verify(dividendPayoutRepository).saveAll(payouts);
    }

    @Test
    @DisplayName("동시 요청으로 같은 revenueId의 UNIQUE 제약을 위반하면 SETTLEMENT_ALREADY_EXISTS_FOR_REVENUE로 변환한다")
    void translatesRevenueUniqueViolationOnConcurrentInsert() {
        when(settlementBatchRepository.saveAndFlush(any()))
                .thenThrow(constraintViolation("uk_settlement_batches_revenue_id"));

        assertThatThrownBy(() -> settlementBatchWriter.persist(batch(), snapshot(), List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.SETTLEMENT_ALREADY_EXISTS_FOR_REVENUE);
    }

    @Test
    @DisplayName("동시 요청으로 자산별 진행 중 배치 부분 고유 인덱스를 위반하면 SETTLEMENT_IN_PROGRESS_FOR_ASSET으로 변환한다")
    void translatesAssetInProgressUniqueViolationOnConcurrentInsert() {
        when(settlementBatchRepository.saveAndFlush(any()))
                .thenThrow(constraintViolation("uk_settlement_batches_asset_in_progress"));

        assertThatThrownBy(() -> settlementBatchWriter.persist(batch(), snapshot(), List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.SETTLEMENT_IN_PROGRESS_FOR_ASSET);
    }

    @Test
    @DisplayName("알 수 없는 제약 위반이면 원래 예외를 그대로 전파한다")
    void propagatesUnrecognizedConstraintViolation() {
        DataIntegrityViolationException unrecognized = constraintViolation("some_other_constraint");
        when(settlementBatchRepository.saveAndFlush(any())).thenThrow(unrecognized);

        assertThatThrownBy(() -> settlementBatchWriter.persist(batch(), snapshot(), List.of()))
                .isSameAs(unrecognized);
    }

    private SettlementBatch batch() {
        return SettlementBatch.open(ASSET_ID, REVENUE_ID, RECORD_DATE, 1_000_000L);
    }

    private HoldingSnapshot snapshot() {
        return HoldingSnapshot.capture(UUID.randomUUID(), ASSET_ID, RECORD_DATE, 1L, 1, 1L);
    }

    private DataIntegrityViolationException constraintViolation(String constraintName) {
        ConstraintViolationException cause = new ConstraintViolationException(
                "duplicate key value violates unique constraint", new SQLException("duplicate key"), constraintName);
        return new DataIntegrityViolationException("constraint violation", cause);
    }
}