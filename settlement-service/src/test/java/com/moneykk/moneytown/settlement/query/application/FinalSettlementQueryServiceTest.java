package com.moneykk.moneytown.settlement.query.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.repository.FinalSettlementBatchRepository;
import com.moneykk.moneytown.settlement.domain.repository.FinalSettlementPayoutRepository;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import com.moneykk.moneytown.settlement.query.dto.FinalSettlementBatchDetailResponse;
import com.moneykk.moneytown.settlement.query.dto.FinalSettlementPayoutListItemResponse;
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
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinalSettlementQueryServiceTest {

    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final Instant TERMINATED_AT = Instant.parse("2027-03-01T00:00:00Z");

    @Mock
    private FinalSettlementBatchRepository finalSettlementBatchRepository;
    @Mock
    private FinalSettlementPayoutRepository finalSettlementPayoutRepository;

    @InjectMocks
    private FinalSettlementQueryService finalSettlementQueryService;

    @Nested
    @DisplayName("최종 정산 회차 상태 조회")
    class GetFinalSettlementBatch {

        @Test
        @DisplayName("존재하는 회차를 조회하면 payout 상태별 진행 집계와 함께 반환한다")
        void returnsBatchDetailWithProgress() {
            FinalSettlementBatch batch = FinalSettlementBatch.open(ASSET_ID, TERMINATED_AT, 1_000_000L, 900_000_000L);
            batch.markDisbursing();
            when(finalSettlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId()))
                    .thenReturn(Optional.of(batch));
            when(finalSettlementPayoutRepository.countByStatusGrouped(batch.getId()))
                    .thenReturn(List.of(
                            statusCount(PayoutStatus.PAID, 870L),
                            statusCount(PayoutStatus.DEAD_LETTER, 2L),
                            statusCount(PayoutStatus.QUEUED, 20L),
                            statusCount(PayoutStatus.PROCESSING, 5L),
                            statusCount(PayoutStatus.RETRYING, 3L)
                    ));

            FinalSettlementBatchDetailResponse response =
                    finalSettlementQueryService.getFinalSettlementBatch(batch.getId());

            assertThat(response.finalSettlementBatchId()).isEqualTo(batch.getId());
            assertThat(response.assetId()).isEqualTo(ASSET_ID);
            assertThat(response.terminatedAt()).isEqualTo(TERMINATED_AT);
            assertThat(response.unitPrice()).isEqualTo(1_000_000L);
            assertThat(response.totalAmount()).isEqualTo(900_000_000L);
            assertThat(response.status()).isEqualTo(batch.getStatus());
            FinalSettlementBatchDetailResponse.Progress progress = response.progress();
            assertThat(progress.totalCount()).isEqualTo(900L);
            assertThat(progress.paidCount()).isEqualTo(870L);
            assertThat(progress.failedCount()).isEqualTo(2L);
            assertThat(progress.pendingCount()).isEqualTo(28L);
        }

        @Test
        @DisplayName("payout이 하나도 없으면 모든 집계가 0이다")
        void returnsZeroedProgressWhenNoPayouts() {
            FinalSettlementBatch batch = FinalSettlementBatch.open(ASSET_ID, TERMINATED_AT, 1_000_000L, 900_000_000L);
            when(finalSettlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId()))
                    .thenReturn(Optional.of(batch));
            when(finalSettlementPayoutRepository.countByStatusGrouped(batch.getId()))
                    .thenReturn(List.of());

            FinalSettlementBatchDetailResponse response =
                    finalSettlementQueryService.getFinalSettlementBatch(batch.getId());

            assertThat(response.progress().totalCount()).isZero();
        }

        @Test
        @DisplayName("존재하지 않는 회차를 조회하면 예외가 발생한다")
        void throwsWhenBatchNotFound() {
            UUID finalSettlementBatchId = UUID.randomUUID();
            when(finalSettlementBatchRepository.findByIdAndIsDeletedFalse(finalSettlementBatchId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> finalSettlementQueryService.getFinalSettlementBatch(finalSettlementBatchId))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getErrorCode())
                    .isEqualTo(SettlementErrorCode.FINAL_SETTLEMENT_BATCH_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("회차별 개별 반환 내역 조회")
    class GetPayouts {

        @Test
        @DisplayName("status 미지정 시 전체 반환 내역을 amount 내림차순으로 반환한다")
        void returnsAllPayoutsSortedByAmountDescWhenStatusOmitted() {
            UUID batchId = UUID.randomUUID();
            FinalSettlementPayout payout = FinalSettlementPayout.queue(batchId, UUID.randomUUID(), 30L, 30_000_000L);
            Pageable requestedPageable = PageRequest.of(0, 20);
            Pageable expectedPageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "amount"));
            when(finalSettlementBatchRepository.existsByIdAndIsDeletedFalse(batchId)).thenReturn(true);
            when(finalSettlementPayoutRepository.findByFinalSettlementBatchIdAndIsDeletedFalse(batchId, expectedPageable))
                    .thenReturn(new PageImpl<>(List.of(payout), expectedPageable, 1));

            PageResponse<FinalSettlementPayoutListItemResponse> response =
                    finalSettlementQueryService.getPayouts(batchId, null, requestedPageable);

            assertThat(response.content()).hasSize(1);
            FinalSettlementPayoutListItemResponse item = response.content().get(0);
            assertThat(item.finalSettlementPayoutId()).isEqualTo(payout.getId());
            assertThat(item.investorId()).isEqualTo(payout.getInvestorId());
            assertThat(item.quantity()).isEqualTo(payout.getQuantity());
            assertThat(item.amount()).isEqualTo(payout.getAmount());
            assertThat(item.status()).isEqualTo(payout.getStatus());
            assertThat(item.retryCount()).isEqualTo(payout.getRetryCount());
            assertThat(response.totalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("status로 필터링하여 해당 상태의 반환 내역만 반환한다")
        void filtersPayoutsByStatus() {
            UUID batchId = UUID.randomUUID();
            FinalSettlementPayout payout = FinalSettlementPayout.queue(batchId, UUID.randomUUID(), 30L, 30_000_000L);
            payout.markProcessing();
            payout.markPaid();
            Pageable requestedPageable = PageRequest.of(0, 20);
            Pageable expectedPageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "amount"));
            when(finalSettlementBatchRepository.existsByIdAndIsDeletedFalse(batchId)).thenReturn(true);
            when(finalSettlementPayoutRepository.findByFinalSettlementBatchIdAndStatusAndIsDeletedFalse(batchId, PayoutStatus.PAID, expectedPageable))
                    .thenReturn(new PageImpl<>(List.of(payout), expectedPageable, 1));

            PageResponse<FinalSettlementPayoutListItemResponse> response =
                    finalSettlementQueryService.getPayouts(batchId, PayoutStatus.PAID, requestedPageable);

            assertThat(response.content()).hasSize(1);
            assertThat(response.content().get(0).status()).isEqualTo(PayoutStatus.PAID);
        }

        @Test
        @DisplayName("status=DEAD_LETTER로 필터링하면 retryCount 내림차순으로 정렬한다")
        void sortsByRetryCountDescWhenFilteredByDeadLetter() {
            UUID batchId = UUID.randomUUID();
            FinalSettlementPayout payout = FinalSettlementPayout.queue(batchId, UUID.randomUUID(), 30L, 30_000_000L);
            payout.markDeadLetter();
            Pageable requestedPageable = PageRequest.of(0, 20);
            Pageable expectedPageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "retryCount"));
            when(finalSettlementBatchRepository.existsByIdAndIsDeletedFalse(batchId)).thenReturn(true);
            when(finalSettlementPayoutRepository.findByFinalSettlementBatchIdAndStatusAndIsDeletedFalse(batchId, PayoutStatus.DEAD_LETTER, expectedPageable))
                    .thenReturn(new PageImpl<>(List.of(payout), expectedPageable, 1));

            PageResponse<FinalSettlementPayoutListItemResponse> response =
                    finalSettlementQueryService.getPayouts(batchId, PayoutStatus.DEAD_LETTER, requestedPageable);

            assertThat(response.content()).hasSize(1);
            assertThat(response.content().get(0).status()).isEqualTo(PayoutStatus.DEAD_LETTER);
        }

        @Test
        @DisplayName("존재하지 않는 회차를 조회하면 예외가 발생한다")
        void throwsWhenBatchNotFound() {
            UUID batchId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 20);
            when(finalSettlementBatchRepository.existsByIdAndIsDeletedFalse(batchId)).thenReturn(false);

            assertThatThrownBy(() -> finalSettlementQueryService.getPayouts(batchId, null, pageable))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getErrorCode())
                    .isEqualTo(SettlementErrorCode.FINAL_SETTLEMENT_BATCH_NOT_FOUND);
        }
    }

    private FinalSettlementPayoutRepository.PayoutStatusCount statusCount(PayoutStatus status, long count) {
        return new FinalSettlementPayoutRepository.PayoutStatusCount() {
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