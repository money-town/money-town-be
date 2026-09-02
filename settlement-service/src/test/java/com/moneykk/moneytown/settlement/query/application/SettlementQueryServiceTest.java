package com.moneykk.moneytown.settlement.query.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.settlement.domain.entity.DividendPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.domain.repository.DividendPayoutRepository;
import com.moneykk.moneytown.settlement.domain.repository.SettlementBatchRepository;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import com.moneykk.moneytown.settlement.query.dto.DividendPayoutListItemResponse;
import com.moneykk.moneytown.settlement.query.dto.SettlementBatchDetailResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementQueryServiceTest {

    private static final UUID SETTLEMENT_BATCH_ID = UUID.randomUUID();
    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final UUID REVENUE_ID = UUID.randomUUID();
    private static final LocalDate RECORD_DATE = LocalDate.of(2026, 9, 1);

    @Mock
    private SettlementBatchRepository settlementBatchRepository;
    @Mock
    private DividendPayoutRepository dividendPayoutRepository;

    @InjectMocks
    private SettlementQueryService settlementQueryService;

    @Nested
    @DisplayName("정산 회차 상태 조회")
    class GetSettlementBatch {

        @Test
        @DisplayName("존재하는 회차를 조회하면 payout 상태별 집계와 함께 반환한다")
        void returnsBatchDetailWithPayoutSummary() {
            SettlementBatch batch = SettlementBatch.open(ASSET_ID, REVENUE_ID, RECORD_DATE, 10_000L, 0L);
            batch.markSnapshotTaken();
            batch.markCalculated(0L);
            when(settlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId()))
                    .thenReturn(Optional.of(batch));
            when(dividendPayoutRepository.countByStatusGrouped(batch.getId()))
                    .thenReturn(List.of(
                            statusCount(PayoutStatus.PAID, 3L),
                            statusCount(PayoutStatus.DEAD_LETTER, 1L),
                            statusCount(PayoutStatus.QUEUED, 2L),
                            statusCount(PayoutStatus.PROCESSING, 1L),
                            statusCount(PayoutStatus.RETRYING, 1L)
                    ));

            SettlementBatchDetailResponse response = settlementQueryService.getSettlementBatch(batch.getId());

            assertThat(response.settlementBatchId()).isEqualTo(batch.getId());
            assertThat(response.assetId()).isEqualTo(ASSET_ID);
            assertThat(response.revenueId()).isEqualTo(REVENUE_ID);
            assertThat(response.status()).isEqualTo(batch.getStatus());
            SettlementBatchDetailResponse.PayoutSummary summary = response.payoutSummary();
            assertThat(summary.totalCount()).isEqualTo(8L);
            assertThat(summary.paidCount()).isEqualTo(3L);
            assertThat(summary.failedCount()).isEqualTo(1L);
            assertThat(summary.pendingCount()).isEqualTo(4L);
        }

        @Test
        @DisplayName("payout이 하나도 없으면 모든 집계가 0이다")
        void returnsZeroedSummaryWhenNoPayouts() {
            SettlementBatch batch = SettlementBatch.open(ASSET_ID, REVENUE_ID, RECORD_DATE, 10_000L, 0L);
            when(settlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId()))
                    .thenReturn(Optional.of(batch));
            when(dividendPayoutRepository.countByStatusGrouped(batch.getId()))
                    .thenReturn(List.of());

            SettlementBatchDetailResponse response = settlementQueryService.getSettlementBatch(batch.getId());

            assertThat(response.payoutSummary().totalCount()).isZero();
        }

        @Test
        @DisplayName("존재하지 않는 회차를 조회하면 예외가 발생한다")
        void throwsWhenBatchNotFound() {
            when(settlementBatchRepository.findByIdAndIsDeletedFalse(SETTLEMENT_BATCH_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> settlementQueryService.getSettlementBatch(SETTLEMENT_BATCH_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getErrorCode())
                    .isEqualTo(SettlementErrorCode.SETTLEMENT_BATCH_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("회차별 개별 지급 내역 조회")
    class GetPayouts {

        @Test
        @DisplayName("존재하는 회차의 지급 내역을 페이지로 반환한다")
        void returnsPayoutPage() {
            UUID batchId = UUID.randomUUID();
            DividendPayout payout = DividendPayout.queue(batchId, UUID.randomUUID(), BigDecimal.valueOf(0.00234000), 21_060L);
            Pageable pageable = PageRequest.of(0, 20);
            when(settlementBatchRepository.existsByIdAndIsDeletedFalse(batchId)).thenReturn(true);
            when(dividendPayoutRepository.findBySettlementBatchIdAndIsDeletedFalse(batchId, pageable))
                    .thenReturn(new PageImpl<>(List.of(payout), pageable, 1));

            PageResponse<DividendPayoutListItemResponse> response = settlementQueryService.getPayouts(batchId, pageable);

            assertThat(response.content()).hasSize(1);
            DividendPayoutListItemResponse item = response.content().get(0);
            assertThat(item.dividendPayoutId()).isEqualTo(payout.getId());
            assertThat(item.investorId()).isEqualTo(payout.getInvestorId());
            assertThat(item.shareRatio()).isEqualTo(payout.getShareRatio());
            assertThat(item.amount()).isEqualTo(payout.getAmount());
            assertThat(item.status()).isEqualTo(payout.getStatus());
            assertThat(item.retryCount()).isEqualTo(payout.getRetryCount());
            assertThat(response.totalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("존재하지 않는 회차를 조회하면 예외가 발생한다")
        void throwsWhenBatchNotFound() {
            UUID batchId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 20);
            when(settlementBatchRepository.existsByIdAndIsDeletedFalse(batchId)).thenReturn(false);

            assertThatThrownBy(() -> settlementQueryService.getPayouts(batchId, pageable))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getErrorCode())
                    .isEqualTo(SettlementErrorCode.SETTLEMENT_BATCH_NOT_FOUND);
        }
    }

    private DividendPayoutRepository.PayoutStatusCount statusCount(PayoutStatus status, long count) {
        return new DividendPayoutRepository.PayoutStatusCount() {
            @Override
            public PayoutStatus getStatus() {
                return status;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }
}