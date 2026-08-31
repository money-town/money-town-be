package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.settlement.command.dto.SettlementBatchResponse;
import com.moneykk.moneytown.settlement.domain.entity.DividendPayout;
import com.moneykk.moneytown.settlement.domain.entity.HoldingSnapshot;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import com.moneykk.moneytown.settlement.domain.repository.DividendPayoutRepository;
import com.moneykk.moneytown.settlement.domain.repository.HoldingSnapshotRepository;
import com.moneykk.moneytown.settlement.domain.repository.SettlementBatchRepository;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import com.moneykk.moneytown.settlement.infrastructure.client.AssetHoldingsSnapshotFetcher;
import com.moneykk.moneytown.settlement.infrastructure.client.AssetServiceClient;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.HoldingItem;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.RevenueResponse;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.RevenueTransferStatus;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementCommandServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final UUID REVENUE_ID = UUID.randomUUID();
    private static final LocalDate RECORD_DATE = LocalDate.of(2026, 9, 1);
    private static final Instant OCCURRED_AT = RECORD_DATE.atStartOfDay(SEOUL).toInstant();

    @Mock
    private SettlementBatchRepository settlementBatchRepository;
    @Mock
    private HoldingSnapshotRepository holdingSnapshotRepository;
    @Mock
    private DividendPayoutRepository dividendPayoutRepository;
    @Mock
    private AssetServiceClient assetServiceClient;
    @Mock
    private AssetHoldingsSnapshotFetcher assetHoldingsSnapshotFetcher;

    @InjectMocks
    private SettlementCommandService settlementCommandService;

    @Test
    @DisplayName("정산 회차를 정상적으로 개시한다")
    void opensSettlementBatchSuccessfully() {
        stubNoExistingBatch();
        RevenueResponse revenue = revenue(BigDecimal.valueOf(10_000_000), BigDecimal.ZERO, BigDecimal.ZERO,
                OCCURRED_AT, RECORD_DATE, RevenueTransferStatus.PENDING);
        stubRevenue(revenue);
        stubNoPreviousCompletedBatch();

        UUID investorId = UUID.randomUUID();
        when(assetHoldingsSnapshotFetcher.fetchAll(ASSET_ID, RECORD_DATE))
                .thenReturn(aggregated(100L, List.of(new HoldingItem(UUID.randomUUID(), investorId, 100L))));

        SettlementBatchResponse response = settlementCommandService.openBatch(ASSET_ID, REVENUE_ID);

        assertThat(response.assetId()).isEqualTo(ASSET_ID);
        assertThat(response.revenueId()).isEqualTo(REVENUE_ID);
        assertThat(response.recordDate()).isEqualTo(RECORD_DATE);
        assertThat(response.totalAmount()).isEqualTo(10_000_000L);
        assertThat(response.carriedInAmount()).isZero();
        assertThat(response.remainderAmount()).isZero();
        assertThat(response.status()).isEqualTo(SettlementStatus.CALCULATED);
        assertThat(response.payoutCount()).isEqualTo(1);

        ArgumentCaptor<SettlementBatch> batchCaptor = ArgumentCaptor.forClass(SettlementBatch.class);
        verify(settlementBatchRepository).saveAndFlush(batchCaptor.capture());
        assertThat(batchCaptor.getValue().getStatus()).isEqualTo(SettlementStatus.CALCULATED);
        assertThat(batchCaptor.getValue().getTotalAmount()).isEqualTo(10_000_000L);

        ArgumentCaptor<HoldingSnapshot> snapshotCaptor = ArgumentCaptor.forClass(HoldingSnapshot.class);
        verify(holdingSnapshotRepository).save(snapshotCaptor.capture());
        assertThat(snapshotCaptor.getValue().getTotalQuantity()).isEqualTo(100L);
        assertThat(snapshotCaptor.getValue().getTotalHolders()).isEqualTo(1);
        assertThat(snapshotCaptor.getValue().getTotalShareQuantity()).isEqualTo(100L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DividendPayout>> payoutsCaptor = ArgumentCaptor.forClass(List.class);
        verify(dividendPayoutRepository).saveAll(payoutsCaptor.capture());
        assertThat(payoutsCaptor.getValue()).hasSize(1);
        assertThat(payoutsCaptor.getValue().get(0).getInvestorId()).isEqualTo(investorId);
        assertThat(payoutsCaptor.getValue().get(0).getAmount()).isEqualTo(10_000_000L);
    }

    @Nested
    @DisplayName("중복/동시 진행 회차 가드")
    class DuplicateOrConcurrentBatchGuard {

        @Test
        @DisplayName("이미 해당 수익 건으로 정산 회차가 있으면 예외")
        void rejectsWhenBatchAlreadyExistsForRevenue() {
            when(settlementBatchRepository.existsByRevenueIdAndIsDeletedFalse(REVENUE_ID)).thenReturn(true);

            assertThatThrownBy(() -> settlementCommandService.openBatch(ASSET_ID, REVENUE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(SettlementErrorCode.SETTLEMENT_ALREADY_EXISTS_FOR_REVENUE);

            verifyNoInteractions(assetServiceClient);
        }

        @Test
        @DisplayName("자산에 이미 진행 중인 회차가 있으면 예외")
        void rejectsWhenAssetHasBatchInProgress() {
            when(settlementBatchRepository.existsByRevenueIdAndIsDeletedFalse(REVENUE_ID)).thenReturn(false);
            when(settlementBatchRepository.existsByAssetIdAndStatusNotAndIsDeletedFalse(ASSET_ID, SettlementStatus.COMPLETED))
                    .thenReturn(true);

            assertThatThrownBy(() -> settlementCommandService.openBatch(ASSET_ID, REVENUE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(SettlementErrorCode.SETTLEMENT_IN_PROGRESS_FOR_ASSET);

            verifyNoInteractions(assetServiceClient);
        }

        @Test
        @DisplayName("동시 요청으로 같은 revenueId의 UNIQUE 제약을 위반하면 SETTLEMENT_ALREADY_EXISTS_FOR_REVENUE로 변환한다")
        void translatesRevenueUniqueViolationOnConcurrentInsert() {
            stubHappyPathUpToInsert();
            when(settlementBatchRepository.saveAndFlush(any()))
                    .thenThrow(constraintViolation("uk_settlement_batches_revenue_id"));

            assertThatThrownBy(() -> settlementCommandService.openBatch(ASSET_ID, REVENUE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(SettlementErrorCode.SETTLEMENT_ALREADY_EXISTS_FOR_REVENUE);
        }

        @Test
        @DisplayName("동시 요청으로 자산별 진행 중 배치 부분 고유 인덱스를 위반하면 SETTLEMENT_IN_PROGRESS_FOR_ASSET으로 변환한다")
        void translatesAssetInProgressUniqueViolationOnConcurrentInsert() {
            stubHappyPathUpToInsert();
            when(settlementBatchRepository.saveAndFlush(any()))
                    .thenThrow(constraintViolation("uk_settlement_batches_asset_in_progress"));

            assertThatThrownBy(() -> settlementCommandService.openBatch(ASSET_ID, REVENUE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(SettlementErrorCode.SETTLEMENT_IN_PROGRESS_FOR_ASSET);
        }

        @Test
        @DisplayName("알 수 없는 제약 위반이면 원래 예외를 그대로 전파한다")
        void propagatesUnrecognizedConstraintViolation() {
            stubHappyPathUpToInsert();
            DataIntegrityViolationException unrecognized = constraintViolation("some_other_constraint");
            when(settlementBatchRepository.saveAndFlush(any())).thenThrow(unrecognized);

            assertThatThrownBy(() -> settlementCommandService.openBatch(ASSET_ID, REVENUE_ID))
                    .isSameAs(unrecognized);
        }

        private void stubHappyPathUpToInsert() {
            stubNoExistingBatch();
            stubRevenue(revenue(BigDecimal.valueOf(1_000_000), BigDecimal.ZERO, BigDecimal.ZERO,
                    OCCURRED_AT, RECORD_DATE, RevenueTransferStatus.PENDING));
            stubNoPreviousCompletedBatch();
            when(assetHoldingsSnapshotFetcher.fetchAll(ASSET_ID, RECORD_DATE))
                    .thenReturn(aggregated(1L, List.of(new HoldingItem(UUID.randomUUID(), UUID.randomUUID(), 1L))));
        }

        private DataIntegrityViolationException constraintViolation(String constraintName) {
            ConstraintViolationException cause = new ConstraintViolationException(
                    "duplicate key value violates unique constraint", new SQLException("duplicate key"), constraintName);
            return new DataIntegrityViolationException("constraint violation", cause);
        }
    }

    @Nested
    @DisplayName("수익 데이터 검증")
    class RevenueValidation {

        @ParameterizedTest(name = "gross={0}, expense={1}, fee={2}")
        @MethodSource("com.moneykk.moneytown.settlement.command.application.SettlementCommandServiceTest#invalidAmounts")
        @DisplayName("수익 금액이 올바르지 않으면 예외")
        void rejectsInvalidAmounts(BigDecimal gross, BigDecimal expense, BigDecimal fee) {
            stubNoExistingBatch();
            stubRevenue(revenue(gross, expense, fee, OCCURRED_AT, RECORD_DATE, RevenueTransferStatus.PENDING));

            assertThatThrownBy(() -> settlementCommandService.openBatch(ASSET_ID, REVENUE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(SettlementErrorCode.REVENUE_AMOUNT_INVALID);
        }

        @Test
        @DisplayName("수익이 PENDING 상태가 아니면 예외")
        void rejectsWhenRevenueNotPending() {
            stubNoExistingBatch();
            stubRevenue(revenue(BigDecimal.valueOf(1_000_000), BigDecimal.ZERO, BigDecimal.ZERO,
                    OCCURRED_AT, RECORD_DATE, RevenueTransferStatus.TRANSFERRED));

            assertThatThrownBy(() -> settlementCommandService.openBatch(ASSET_ID, REVENUE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(SettlementErrorCode.REVENUE_NOT_READY);
        }
    }

    @Nested
    @DisplayName("배당가능총액 계산")
    class DistributableAmountRules {

        @Test
        @DisplayName("배당가능총액이 0원 이하면 예외")
        void rejectsWhenDistributableAmountNotPositive() {
            stubNoExistingBatch();
            stubRevenue(revenue(BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(1_000_000), BigDecimal.ZERO,
                    OCCURRED_AT, RECORD_DATE, RevenueTransferStatus.PENDING));
            stubNoPreviousCompletedBatch();

            assertThatThrownBy(() -> settlementCommandService.openBatch(ASSET_ID, REVENUE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(SettlementErrorCode.DISTRIBUTABLE_AMOUNT_NOT_POSITIVE);

            verifyNoInteractions(holdingSnapshotRepository);
        }

        @Test
        @DisplayName("직전 완료 회차의 잔여금이 이번 회차 총액에 이월되고, 소스 배치는 이월 대상으로 마킹된다")
        void carriesInPreviousRemainder() {
            stubNoExistingBatch();
            stubRevenue(revenue(BigDecimal.valueOf(1_000_000), BigDecimal.ZERO, BigDecimal.ZERO,
                    OCCURRED_AT, RECORD_DATE, RevenueTransferStatus.PENDING));

            SettlementBatch previousCompletedBatch = SettlementBatch.open(ASSET_ID, UUID.randomUUID(),
                    RECORD_DATE.minusMonths(1), 500_000L, 0L);
            previousCompletedBatch.markSnapshotTaken();
            previousCompletedBatch.markCalculated(777L);
            ReflectionTestUtils.setField(previousCompletedBatch, "status", SettlementStatus.COMPLETED);
            when(settlementBatchRepository
                    .findFirstByAssetIdAndStatusAndCarriedOutToBatchIdIsNullAndIsDeletedFalseOrderByRecordDateDescCreatedAtDesc(
                            ASSET_ID, SettlementStatus.COMPLETED))
                    .thenReturn(Optional.of(previousCompletedBatch));

            when(assetHoldingsSnapshotFetcher.fetchAll(ASSET_ID, RECORD_DATE))
                    .thenReturn(aggregated(1L, List.of(new HoldingItem(UUID.randomUUID(), UUID.randomUUID(), 1L))));

            SettlementBatchResponse response = settlementCommandService.openBatch(ASSET_ID, REVENUE_ID);

            assertThat(response.carriedInAmount()).isEqualTo(777L);
            assertThat(response.totalAmount()).isEqualTo(1_000_777L);
            assertThat(previousCompletedBatch.getCarriedOutToBatchId()).isEqualTo(response.settlementBatchId());
            verify(settlementBatchRepository).save(previousCompletedBatch);
        }
    }

    @Nested
    @DisplayName("보유지분 스냅샷 처리")
    class HoldingsSnapshotHandling {

        @Test
        @DisplayName("전체 보유수량이 유효하지 않으면 예외")
        void rejectsWhenTotalHoldingQuantityInvalid() {
            stubNoExistingBatch();
            stubRevenue(revenue(BigDecimal.valueOf(1_000_000), BigDecimal.ZERO, BigDecimal.ZERO,
                    OCCURRED_AT, RECORD_DATE, RevenueTransferStatus.PENDING));
            stubNoPreviousCompletedBatch();

            when(assetHoldingsSnapshotFetcher.fetchAll(ASSET_ID, RECORD_DATE)).thenReturn(aggregated(0L, List.of()));

            assertThatThrownBy(() -> settlementCommandService.openBatch(ASSET_ID, REVENUE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(SettlementErrorCode.HOLDING_SNAPSHOT_INVALID);

            verify(settlementBatchRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("보유자가 여럿이면 각각 payout으로 생성된다")
        void createsPayoutForEachHolder() {
            stubNoExistingBatch();
            stubRevenue(revenue(BigDecimal.valueOf(300), BigDecimal.ZERO, BigDecimal.ZERO,
                    OCCURRED_AT, RECORD_DATE, RevenueTransferStatus.PENDING));
            stubNoPreviousCompletedBatch();

            UUID investor1 = UUID.randomUUID();
            UUID investor2 = UUID.randomUUID();
            when(assetHoldingsSnapshotFetcher.fetchAll(ASSET_ID, RECORD_DATE)).thenReturn(aggregated(3L, List.of(
                    new HoldingItem(UUID.randomUUID(), investor1, 1L),
                    new HoldingItem(UUID.randomUUID(), investor2, 2L))));

            SettlementBatchResponse response = settlementCommandService.openBatch(ASSET_ID, REVENUE_ID);

            assertThat(response.payoutCount()).isEqualTo(2);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<DividendPayout>> payoutsCaptor = ArgumentCaptor.forClass(List.class);
            verify(dividendPayoutRepository).saveAll(payoutsCaptor.capture());
            assertThat(payoutsCaptor.getValue())
                    .extracting(DividendPayout::getInvestorId)
                    .containsExactlyInAnyOrder(investor1, investor2);
        }
    }

    @Nested
    @DisplayName("정산 회차 재시도")
    class RetryBatch {

        @Test
        @DisplayName("실패 건을 QUEUED로 되돌리고 배치 상태를 DISBURSING으로 전환한다")
        void requeuesDeadLetterPayoutsAndMarksDisbursing() {
            SettlementBatch batch = batchWithStatus(SettlementStatus.FAILED);
            when(settlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId())).thenReturn(Optional.of(batch));

            DividendPayout deadLetterPayout = deadLetterPayout(batch.getId());
            when(dividendPayoutRepository.findBySettlementBatchIdAndStatusAndIsDeletedFalse(batch.getId(), PayoutStatus.DEAD_LETTER))
                    .thenReturn(List.of(deadLetterPayout));

            SettlementBatchResponse response = settlementCommandService.retryBatch(batch.getId());

            assertThat(response.status()).isEqualTo(SettlementStatus.DISBURSING);
            assertThat(response.payoutCount()).isEqualTo(1);

            ArgumentCaptor<SettlementBatch> batchCaptor = ArgumentCaptor.forClass(SettlementBatch.class);
            verify(settlementBatchRepository).save(batchCaptor.capture());
            assertThat(batchCaptor.getValue().getStatus()).isEqualTo(SettlementStatus.DISBURSING);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<DividendPayout>> payoutsCaptor = ArgumentCaptor.forClass(List.class);
            verify(dividendPayoutRepository).saveAll(payoutsCaptor.capture());
            assertThat(payoutsCaptor.getValue()).hasSize(1);
            assertThat(payoutsCaptor.getValue().get(0).getStatus()).isEqualTo(PayoutStatus.QUEUED);
            // 재처리는 새로 3번의 시도 기회를 줘야 하므로 requeue()가 retryCount를 0으로 초기화한다.
            assertThat(payoutsCaptor.getValue().get(0).getRetryCount()).isZero();
        }

        @Test
        @DisplayName("PARTIAL_FAILED 상태의 배치도 재시도할 수 있다")
        void allowsRetryWhenPartiallyFailed() {
            SettlementBatch batch = batchWithStatus(SettlementStatus.PARTIAL_FAILED);
            when(settlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId())).thenReturn(Optional.of(batch));
            when(dividendPayoutRepository.findBySettlementBatchIdAndStatusAndIsDeletedFalse(batch.getId(), PayoutStatus.DEAD_LETTER))
                    .thenReturn(List.of());

            SettlementBatchResponse response = settlementCommandService.retryBatch(batch.getId());

            assertThat(response.status()).isEqualTo(SettlementStatus.DISBURSING);
            assertThat(response.payoutCount()).isZero();
        }

        @Test
        @DisplayName("존재하지 않는 정산 회차면 예외")
        void rejectsWhenBatchNotFound() {
            UUID unknownBatchId = UUID.randomUUID();
            when(settlementBatchRepository.findByIdAndIsDeletedFalse(unknownBatchId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> settlementCommandService.retryBatch(unknownBatchId))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(SettlementErrorCode.SETTLEMENT_BATCH_NOT_FOUND);

            verifyNoInteractions(dividendPayoutRepository);
        }

        @Test
        @DisplayName("FAILED/PARTIAL_FAILED 상태가 아니면 예외")
        void rejectsWhenBatchNotRetryable() {
            SettlementBatch batch = batchWithStatus(SettlementStatus.CALCULATED);
            when(settlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId())).thenReturn(Optional.of(batch));

            assertThatThrownBy(() -> settlementCommandService.retryBatch(batch.getId()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(SettlementErrorCode.SETTLEMENT_BATCH_NOT_RETRYABLE);

            verifyNoInteractions(dividendPayoutRepository);
        }

        private SettlementBatch batchWithStatus(SettlementStatus status) {
            SettlementBatch batch = SettlementBatch.open(ASSET_ID, UUID.randomUUID(), RECORD_DATE, 1_000_000L, 0L);
            ReflectionTestUtils.setField(batch, "status", status);
            return batch;
        }

        private DividendPayout deadLetterPayout(UUID batchId) {
            DividendPayout payout = DividendPayout.queue(batchId, UUID.randomUUID(), BigDecimal.ONE, 1_000_000L);
            ReflectionTestUtils.setField(payout, "status", PayoutStatus.DEAD_LETTER);
            ReflectionTestUtils.setField(payout, "retryCount", 3);
            return payout;
        }
    }

    static Stream<Arguments> invalidAmounts() {
        return Stream.of(
                Arguments.of(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                Arguments.of(BigDecimal.valueOf(-1), BigDecimal.ZERO, BigDecimal.ZERO),
                Arguments.of(BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(-1), BigDecimal.ZERO),
                Arguments.of(BigDecimal.valueOf(1_000_000), BigDecimal.ZERO, BigDecimal.valueOf(-1))
        );
    }

    private void stubNoExistingBatch() {
        when(settlementBatchRepository.existsByRevenueIdAndIsDeletedFalse(REVENUE_ID)).thenReturn(false);
        when(settlementBatchRepository.existsByAssetIdAndStatusNotAndIsDeletedFalse(ASSET_ID, SettlementStatus.COMPLETED))
                .thenReturn(false);
    }

    private void stubNoPreviousCompletedBatch() {
        when(settlementBatchRepository
                .findFirstByAssetIdAndStatusAndCarriedOutToBatchIdIsNullAndIsDeletedFalseOrderByRecordDateDescCreatedAtDesc(
                        ASSET_ID, SettlementStatus.COMPLETED))
                .thenReturn(Optional.empty());
    }

    private void stubRevenue(RevenueResponse revenue) {
        when(assetServiceClient.getRevenue(ASSET_ID, REVENUE_ID)).thenReturn(ApiResponse.success(revenue, null));
    }

    private RevenueResponse revenue(BigDecimal gross, BigDecimal expense, BigDecimal fee,
                                     Instant occurredAt, LocalDate recordDate, RevenueTransferStatus transferStatus) {
        return new RevenueResponse(REVENUE_ID, ASSET_ID, "RENT", "PROPERTY_MANAGER", "REF-1",
                gross, expense, fee, occurredAt, recordDate, transferStatus, occurredAt);
    }

    private AssetHoldingsSnapshotFetcher.Aggregated aggregated(Long totalHoldingQuantity, List<HoldingItem> items) {
        return new AssetHoldingsSnapshotFetcher.Aggregated(items, totalHoldingQuantity);
    }
}