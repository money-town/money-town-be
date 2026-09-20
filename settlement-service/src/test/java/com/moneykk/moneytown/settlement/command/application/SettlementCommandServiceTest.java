package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.settlement.command.dto.SettlementBatchResponse;
import com.moneykk.moneytown.settlement.domain.entity.DividendPayout;
import com.moneykk.moneytown.settlement.domain.entity.HoldingSnapshot;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.entity.ResolutionType;
import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import com.moneykk.moneytown.settlement.domain.repository.DividendPayoutRepository;
import com.moneykk.moneytown.settlement.domain.repository.SettlementBatchRepository;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import com.moneykk.moneytown.settlement.infrastructure.client.AssetHoldingsSnapshotFetcher;
import com.moneykk.moneytown.settlement.infrastructure.client.AssetServiceClient;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.HoldingItem;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.RevenueResponse;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.RevenueTransferStatus;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    private static final String ADMIN_ROLE = "ADMIN";
    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final UUID REVENUE_ID = UUID.randomUUID();
    // 배당 기준일 = periodEnd를 기준일로 사용.
    private static final LocalDate RECORD_DATE = LocalDate.of(2026, 9, 1);

    @Mock
    private SettlementBatchRepository settlementBatchRepository;
    @Mock
    private DividendPayoutRepository dividendPayoutRepository;
    @Mock
    private DividendPayoutWriter dividendPayoutWriter;
    @Mock
    private AssetServiceClient assetServiceClient;
    @Mock
    private AssetHoldingsSnapshotFetcher assetHoldingsSnapshotFetcher;
    @Mock
    private SettlementBatchWriter settlementBatchWriter;

    @InjectMocks
    private SettlementCommandService settlementCommandService;

    @Test
    @DisplayName("정산 회차를 정상적으로 개시한다")
    void opensSettlementBatchSuccessfully() {
        stubNoExistingBatch();
        RevenueResponse revenue = revenue(BigDecimal.valueOf(10_000_000), BigDecimal.ZERO, BigDecimal.ZERO,
                RevenueTransferStatus.READY);
        stubRevenue(revenue);

        UUID investorId = UUID.randomUUID();
        when(assetHoldingsSnapshotFetcher.fetchAll(ASSET_ID, RECORD_DATE))
                .thenReturn(aggregated(100L, List.of(new HoldingItem(UUID.randomUUID(), investorId, 100L, null))));

        SettlementBatchResponse response = settlementCommandService.openBatch(ADMIN_ROLE, ASSET_ID, REVENUE_ID, null);

        assertThat(response.assetId()).isEqualTo(ASSET_ID);
        assertThat(response.revenueId()).isEqualTo(REVENUE_ID);
        assertThat(response.recordDate()).isEqualTo(RECORD_DATE);
        assertThat(response.totalAmount()).isEqualTo(10_000_000L);
        assertThat(response.status()).isEqualTo(SettlementStatus.CALCULATED);
        assertThat(response.payoutCount()).isEqualTo(1);
        assertThat(response.newlyCreated()).isTrue();

        ArgumentCaptor<SettlementBatch> batchCaptor = ArgumentCaptor.forClass(SettlementBatch.class);
        ArgumentCaptor<HoldingSnapshot> snapshotCaptor = ArgumentCaptor.forClass(HoldingSnapshot.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DividendPayout>> payoutsCaptor = ArgumentCaptor.forClass(List.class);
        verify(settlementBatchWriter).persist(batchCaptor.capture(), snapshotCaptor.capture(), payoutsCaptor.capture());

        assertThat(batchCaptor.getValue().getStatus()).isEqualTo(SettlementStatus.CALCULATED);
        assertThat(batchCaptor.getValue().getTotalAmount()).isEqualTo(10_000_000L);

        assertThat(snapshotCaptor.getValue().getTotalQuantity()).isEqualTo(100L);
        assertThat(snapshotCaptor.getValue().getTotalHolders()).isEqualTo(1);
        assertThat(snapshotCaptor.getValue().getTotalShareQuantity()).isEqualTo(100L);

        assertThat(payoutsCaptor.getValue()).hasSize(1);
        assertThat(payoutsCaptor.getValue().get(0).getInvestorId()).isEqualTo(investorId);
        assertThat(payoutsCaptor.getValue().get(0).getAmount()).isEqualTo(10_000_000L);
    }

    @Test
    @DisplayName("배당 기준일이 명시되면 periodEnd 대신 그 값을 배당 기준일로 사용한다")
    void usesExplicitRecordDateOverrideInsteadOfPeriodEnd() {
        LocalDate explicitRecordDate = RECORD_DATE.plusDays(5);
        stubNoExistingBatch();
        RevenueResponse revenue = revenue(BigDecimal.valueOf(10_000_000), BigDecimal.ZERO, BigDecimal.ZERO,
                RevenueTransferStatus.READY);
        stubRevenue(revenue);

        when(assetHoldingsSnapshotFetcher.fetchAll(ASSET_ID, explicitRecordDate))
                .thenReturn(aggregated(100L, List.of(new HoldingItem(UUID.randomUUID(), UUID.randomUUID(), 100L, null))));

        SettlementBatchResponse response =
                settlementCommandService.openBatch(ADMIN_ROLE, ASSET_ID, REVENUE_ID, explicitRecordDate);

        assertThat(response.recordDate()).isEqualTo(explicitRecordDate);
        verify(assetHoldingsSnapshotFetcher).fetchAll(ASSET_ID, explicitRecordDate);
        verify(assetHoldingsSnapshotFetcher, never()).fetchAll(ASSET_ID, RECORD_DATE);
    }

    @Nested
    @DisplayName("스케줄러 자동 개시 (ADMIN 검사 없음)")
    class OpenBatchAutomatically {

        @Test
        @DisplayName("ADMIN 권한 없이도 openBatch와 동일하게 정산 회차를 정상적으로 개시한다")
        void opensSettlementBatchWithoutAdminRole() {
            stubNoExistingBatch();
            RevenueResponse revenue = revenue(BigDecimal.valueOf(10_000_000), BigDecimal.ZERO, BigDecimal.ZERO,
                    RevenueTransferStatus.READY);
            stubRevenue(revenue);

            UUID investorId = UUID.randomUUID();
            when(assetHoldingsSnapshotFetcher.fetchAll(ASSET_ID, RECORD_DATE))
                    .thenReturn(aggregated(100L, List.of(new HoldingItem(UUID.randomUUID(), investorId, 100L, null))));

            SettlementBatchResponse response = settlementCommandService.openBatchAutomatically(ASSET_ID, REVENUE_ID);

            assertThat(response.assetId()).isEqualTo(ASSET_ID);
            assertThat(response.revenueId()).isEqualTo(REVENUE_ID);
            assertThat(response.status()).isEqualTo(SettlementStatus.CALCULATED);
            assertThat(response.payoutCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("이미 해당 수익 건으로 정산 회차가 있으면 예외 없이 기존 회차를 그대로 반환한다 (kafka.md 4-2절 멱등)")
        void returnsExistingBatchWithoutErrorWhenAlreadyExistsForRevenue() {
            SettlementBatch existing = SettlementBatch.open(ASSET_ID, REVENUE_ID, RECORD_DATE, 10_000_000L);
            when(settlementBatchRepository.findByRevenueIdAndIsDeletedFalse(REVENUE_ID)).thenReturn(Optional.of(existing));
            when(dividendPayoutRepository.countBySettlementBatchIdAndIsDeletedFalse(existing.getId())).thenReturn(1L);

            SettlementBatchResponse response = settlementCommandService.openBatchAutomatically(ASSET_ID, REVENUE_ID);

            assertThat(response.settlementBatchId()).isEqualTo(existing.getId());
            assertThat(response.newlyCreated()).isFalse();
            assertThat(response.payoutCount()).isEqualTo(1);
            verifyNoInteractions(assetServiceClient, settlementBatchWriter);
        }
    }

    @Nested
    @DisplayName("ADMIN 권한 검증")
    class AdminAccessControl {

        @Test
        @DisplayName("ADMIN이 아니면 정산 회차 개시 시 예외")
        void rejectsOpenBatchWhenNotAdmin() {
            assertThatThrownBy(() -> settlementCommandService.openBatch("INVESTOR", ASSET_ID, REVENUE_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(SettlementErrorCode.SETTLEMENT_ACCESS_DENIED);

            verifyNoInteractions(settlementBatchRepository, assetServiceClient);
        }

        @Test
        @DisplayName("ADMIN이 아니면 정산 회차 재시도 시 예외")
        void rejectsRetryBatchWhenNotAdmin() {
            assertThatThrownBy(() -> settlementCommandService.retryBatch("INVESTOR", UUID.randomUUID()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(SettlementErrorCode.SETTLEMENT_ACCESS_DENIED);

            verifyNoInteractions(settlementBatchRepository, dividendPayoutRepository);
        }
    }

    @Nested
    @DisplayName("중복/동시 진행 회차 가드")
    class DuplicateOrConcurrentBatchGuard {

        @Test
        @DisplayName("이미 해당 수익 건으로 정산 회차가 있으면 예외 없이 기존 회차를 그대로 반환한다 (kafka.md 4-2절 멱등)")
        void returnsExistingBatchWithoutErrorWhenAlreadyExistsForRevenue() {
            SettlementBatch existing = SettlementBatch.open(ASSET_ID, REVENUE_ID, RECORD_DATE, 10_000_000L);
            when(settlementBatchRepository.findByRevenueIdAndIsDeletedFalse(REVENUE_ID)).thenReturn(Optional.of(existing));
            when(dividendPayoutRepository.countBySettlementBatchIdAndIsDeletedFalse(existing.getId())).thenReturn(0L);

            SettlementBatchResponse response = settlementCommandService.openBatch(ADMIN_ROLE, ASSET_ID, REVENUE_ID, null);

            assertThat(response.settlementBatchId()).isEqualTo(existing.getId());
            assertThat(response.newlyCreated()).isFalse();
            verifyNoInteractions(assetServiceClient);
        }

        @Test
        @DisplayName("기존 배치가 요청한 assetId와 다른 자산 소속이면 예외 (revenueId만으로 조회한 배치를 그대로 신뢰하지 않는다)")
        void rejectsWhenExistingBatchBelongsToDifferentAsset() {
            UUID otherAssetId = UUID.randomUUID();
            SettlementBatch existingForOtherAsset = SettlementBatch.open(otherAssetId, REVENUE_ID, RECORD_DATE, 10_000_000L);
            when(settlementBatchRepository.findByRevenueIdAndIsDeletedFalse(REVENUE_ID))
                    .thenReturn(Optional.of(existingForOtherAsset));

            assertThatThrownBy(() -> settlementCommandService.openBatch(ADMIN_ROLE, ASSET_ID, REVENUE_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(SettlementErrorCode.REVENUE_ASSET_MISMATCH);

            verifyNoInteractions(assetServiceClient, dividendPayoutRepository);
        }

        @Test
        @DisplayName("자산에 이미 진행 중인 회차가 있으면 예외")
        void rejectsWhenAssetHasBatchInProgress() {
            when(settlementBatchRepository.findByRevenueIdAndIsDeletedFalse(REVENUE_ID)).thenReturn(Optional.empty());
            when(settlementBatchRepository.existsByAssetIdAndStatusNotInAndIsDeletedFalse(
                    ASSET_ID, List.of(SettlementStatus.COMPLETED, SettlementStatus.CLOSED_ABANDONED)))
                    .thenReturn(true);

            assertThatThrownBy(() -> settlementCommandService.openBatch(ADMIN_ROLE, ASSET_ID, REVENUE_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(SettlementErrorCode.SETTLEMENT_IN_PROGRESS_FOR_ASSET);

            verifyNoInteractions(assetServiceClient);
        }

        @Test
        @DisplayName("두 요청이 동시에 '기존 배치 없음'을 통과해도, 유니크 제약 위반 쪽은 이긴 쪽의 배치를 멱등 반환한다 (kafka.md 4-2절)")
        void returnsWinnerBatchWhenConcurrentInsertRaces() {
            stubNoExistingBatch();
            stubRevenue(revenue(BigDecimal.valueOf(10_000_000), BigDecimal.ZERO, BigDecimal.ZERO,
                    RevenueTransferStatus.READY));
            when(assetHoldingsSnapshotFetcher.fetchAll(ASSET_ID, RECORD_DATE))
                    .thenReturn(aggregated(100L, List.of(new HoldingItem(UUID.randomUUID(), UUID.randomUUID(), 100L, null))));

            SettlementBatch winnerBatch = SettlementBatch.open(ASSET_ID, REVENUE_ID, RECORD_DATE, 10_000_000L);
            org.mockito.Mockito.doThrow(new BusinessException(SettlementErrorCode.SETTLEMENT_ALREADY_EXISTS_FOR_REVENUE))
                    .when(settlementBatchWriter).persist(any(), any(), any());
            when(settlementBatchRepository.findByRevenueIdAndIsDeletedFalse(REVENUE_ID))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(winnerBatch));
            when(dividendPayoutRepository.countBySettlementBatchIdAndIsDeletedFalse(winnerBatch.getId())).thenReturn(1L);

            SettlementBatchResponse response = settlementCommandService.openBatchAutomatically(ASSET_ID, REVENUE_ID);

            assertThat(response.settlementBatchId()).isEqualTo(winnerBatch.getId());
            assertThat(response.newlyCreated()).isFalse();
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
            stubRevenue(revenue(gross, expense, fee, RevenueTransferStatus.READY));

            assertThatThrownBy(() -> settlementCommandService.openBatch(ADMIN_ROLE, ASSET_ID, REVENUE_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(SettlementErrorCode.REVENUE_AMOUNT_INVALID);
        }

        @Test
        @DisplayName("수익이 READY 상태가 아니면 예외")
        void rejectsWhenRevenueNotReady() {
            stubNoExistingBatch();
            stubRevenue(revenue(BigDecimal.valueOf(1_000_000), BigDecimal.ZERO, BigDecimal.ZERO,
                    RevenueTransferStatus.TRANSFERRED));

            assertThatThrownBy(() -> settlementCommandService.openBatch(ADMIN_ROLE, ASSET_ID, REVENUE_ID, null))
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
                    RevenueTransferStatus.READY));

            assertThatThrownBy(() -> settlementCommandService.openBatch(ADMIN_ROLE, ASSET_ID, REVENUE_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(SettlementErrorCode.DISTRIBUTABLE_AMOUNT_NOT_POSITIVE);

            verifyNoInteractions(settlementBatchWriter);
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
                    RevenueTransferStatus.READY));

            when(assetHoldingsSnapshotFetcher.fetchAll(ASSET_ID, RECORD_DATE)).thenReturn(aggregated(0L, List.of()));

            assertThatThrownBy(() -> settlementCommandService.openBatch(ADMIN_ROLE, ASSET_ID, REVENUE_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(SettlementErrorCode.HOLDING_SNAPSHOT_INVALID);

            verifyNoInteractions(settlementBatchWriter);
        }

        @Test
        @DisplayName("보유자가 여럿이면 각각 payout으로 생성된다")
        void createsPayoutForEachHolder() {
            stubNoExistingBatch();
            stubRevenue(revenue(BigDecimal.valueOf(300), BigDecimal.ZERO, BigDecimal.ZERO,
                    RevenueTransferStatus.READY));

            UUID investor1 = UUID.randomUUID();
            UUID investor2 = UUID.randomUUID();
            when(assetHoldingsSnapshotFetcher.fetchAll(ASSET_ID, RECORD_DATE)).thenReturn(aggregated(3L, List.of(
                    new HoldingItem(UUID.randomUUID(), investor1, 1L, null),
                    new HoldingItem(UUID.randomUUID(), investor2, 2L, null))));

            SettlementBatchResponse response = settlementCommandService.openBatch(ADMIN_ROLE, ASSET_ID, REVENUE_ID, null);

            assertThat(response.payoutCount()).isEqualTo(2);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<DividendPayout>> payoutsCaptor = ArgumentCaptor.forClass(List.class);
            verify(settlementBatchWriter).persist(any(), any(), payoutsCaptor.capture());
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

            SettlementBatchResponse response = settlementCommandService.retryBatch(ADMIN_ROLE, batch.getId());

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

            SettlementBatchResponse response = settlementCommandService.retryBatch(ADMIN_ROLE, batch.getId());

            assertThat(response.status()).isEqualTo(SettlementStatus.DISBURSING);
            assertThat(response.payoutCount()).isZero();
        }

        @Test
        @DisplayName("존재하지 않는 정산 회차면 예외")
        void rejectsWhenBatchNotFound() {
            UUID unknownBatchId = UUID.randomUUID();
            when(settlementBatchRepository.findByIdAndIsDeletedFalse(unknownBatchId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> settlementCommandService.retryBatch(ADMIN_ROLE, unknownBatchId))
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

            assertThatThrownBy(() -> settlementCommandService.retryBatch(ADMIN_ROLE, batch.getId()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(SettlementErrorCode.SETTLEMENT_BATCH_NOT_RETRYABLE);

            verifyNoInteractions(dividendPayoutRepository);
        }

        private SettlementBatch batchWithStatus(SettlementStatus status) {
            SettlementBatch batch = SettlementBatch.open(ASSET_ID, UUID.randomUUID(), RECORD_DATE, 1_000_000L);
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

    @Nested
    @DisplayName("지급 건 포기 처리 (T5)")
    class AbandonPayout {

        @Test
        @DisplayName("포기 처리 후 배치가 CLOSED_ABANDONED로 마감되면 그 상태를 그대로 응답한다")
        void abandonsPayoutAndReturnsClosedBatch() {
            SettlementBatch batch = batchWithStatus(SettlementStatus.PARTIAL_FAILED);
            DividendPayout payout = abandonedPayout(batch.getId());
            when(dividendPayoutWriter.abandonPayout(payout.getId(), ResolutionType.BANK_TRANSFER, "REF-1", null)).thenReturn(payout);

            SettlementBatch closedBatch = batchWithStatus(SettlementStatus.CLOSED_ABANDONED);
            ReflectionTestUtils.setField(closedBatch, "id", batch.getId());
            when(dividendPayoutWriter.updateBatchStatus(batch.getId())).thenReturn(Optional.of(closedBatch));
            when(dividendPayoutRepository.countBySettlementBatchIdAndIsDeletedFalse(batch.getId())).thenReturn(3L);

            SettlementBatchResponse response =
                    settlementCommandService.abandonPayout(ADMIN_ROLE, batch.getId(), payout.getId(), ResolutionType.BANK_TRANSFER, "REF-1", null);

            assertThat(response.status()).isEqualTo(SettlementStatus.CLOSED_ABANDONED);
            assertThat(response.payoutCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("아직 다른 건이 진행 중이라 updateBatchStatus가 비어 있으면 배치를 다시 조회해 현재 상태를 응답한다")
        void fallsBackToRepositoryWhenBatchNotYetFinalized() {
            SettlementBatch batch = batchWithStatus(SettlementStatus.PARTIAL_FAILED);
            DividendPayout payout = abandonedPayout(batch.getId());
            when(dividendPayoutWriter.abandonPayout(payout.getId(), ResolutionType.BANK_TRANSFER, "REF-1", null)).thenReturn(payout);
            when(dividendPayoutWriter.updateBatchStatus(batch.getId())).thenReturn(Optional.empty());
            when(settlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId())).thenReturn(Optional.of(batch));
            when(dividendPayoutRepository.countBySettlementBatchIdAndIsDeletedFalse(batch.getId())).thenReturn(5L);

            SettlementBatchResponse response =
                    settlementCommandService.abandonPayout(ADMIN_ROLE, batch.getId(), payout.getId(), ResolutionType.BANK_TRANSFER, "REF-1", null);

            assertThat(response.status()).isEqualTo(SettlementStatus.PARTIAL_FAILED);
        }

        @Test
        @DisplayName("ADMIN이 아니면 예외")
        void rejectsWhenNotAdmin() {
            assertThatThrownBy(() -> settlementCommandService.abandonPayout("INVESTOR", UUID.randomUUID(), UUID.randomUUID(), ResolutionType.BANK_TRANSFER, "REF-1", null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(SettlementErrorCode.SETTLEMENT_ACCESS_DENIED);

            verifyNoInteractions(dividendPayoutWriter);
        }

        @Test
        @DisplayName("지급 건이 요청한 정산 회차 소속이 아니면 예외")
        void rejectsWhenPayoutBelongsToDifferentBatch() {
            UUID otherBatchId = UUID.randomUUID();
            DividendPayout payout = abandonedPayout(otherBatchId);
            UUID requestedBatchId = UUID.randomUUID();
            when(dividendPayoutWriter.abandonPayout(payout.getId(), ResolutionType.BANK_TRANSFER, "REF-1", null)).thenReturn(payout);

            assertThatThrownBy(() -> settlementCommandService.abandonPayout(ADMIN_ROLE, requestedBatchId, payout.getId(), ResolutionType.BANK_TRANSFER, "REF-1", null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(SettlementErrorCode.PAYOUT_BATCH_MISMATCH);
        }

        @Test
        @DisplayName("resolutionType이 OTHER인데 resolutionNote가 없거나 공백이면 예외 — writer는 호출하지 않는다")
        void rejectsOtherWithoutNote() {
            for (String note : new String[]{null, "", "   "}) {
                assertThatThrownBy(() -> settlementCommandService.abandonPayout(
                        ADMIN_ROLE, UUID.randomUUID(), UUID.randomUUID(), ResolutionType.OTHER, "REF-1", note))
                        .isInstanceOf(BusinessException.class)
                        .extracting(e -> ((BusinessException) e).getErrorCode())
                        .isEqualTo(SettlementErrorCode.RESOLUTION_NOTE_REQUIRED);
            }

            verifyNoInteractions(dividendPayoutWriter);
        }

        @Test
        @DisplayName("resolutionType이 OTHER여도 resolutionNote가 있으면 포기 처리한다")
        void allowsOtherWithNote() {
            SettlementBatch batch = batchWithStatus(SettlementStatus.PARTIAL_FAILED);
            DividendPayout payout = abandonedPayout(batch.getId());
            when(dividendPayoutWriter.abandonPayout(payout.getId(), ResolutionType.OTHER, "REF-1", "현금 지급"))
                    .thenReturn(payout);
            when(dividendPayoutWriter.updateBatchStatus(batch.getId())).thenReturn(Optional.empty());
            when(settlementBatchRepository.findByIdAndIsDeletedFalse(batch.getId())).thenReturn(Optional.of(batch));
            when(dividendPayoutRepository.countBySettlementBatchIdAndIsDeletedFalse(batch.getId())).thenReturn(1L);

            SettlementBatchResponse response = settlementCommandService.abandonPayout(
                    ADMIN_ROLE, batch.getId(), payout.getId(), ResolutionType.OTHER, "REF-1", "현금 지급");

            assertThat(response.status()).isEqualTo(SettlementStatus.PARTIAL_FAILED);
        }

        private SettlementBatch batchWithStatus(SettlementStatus status) {
            SettlementBatch batch = SettlementBatch.open(ASSET_ID, UUID.randomUUID(), RECORD_DATE, 1_000_000L);
            ReflectionTestUtils.setField(batch, "status", status);
            return batch;
        }

        private DividendPayout abandonedPayout(UUID batchId) {
            DividendPayout payout = DividendPayout.queue(batchId, UUID.randomUUID(), BigDecimal.ONE, 1_000_000L);
            ReflectionTestUtils.setField(payout, "status", PayoutStatus.ABANDONED);
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
        when(settlementBatchRepository.findByRevenueIdAndIsDeletedFalse(REVENUE_ID)).thenReturn(Optional.empty());
        when(settlementBatchRepository.existsByAssetIdAndStatusNotInAndIsDeletedFalse(
                ASSET_ID, List.of(SettlementStatus.COMPLETED, SettlementStatus.CLOSED_ABANDONED)))
                .thenReturn(false);
    }

    private void stubRevenue(RevenueResponse revenue) {
        when(assetServiceClient.getRevenue(ASSET_ID, REVENUE_ID, "SYSTEM")).thenReturn(ApiResponse.success(revenue, null));
    }

    private RevenueResponse revenue(BigDecimal gross, BigDecimal expense, BigDecimal fee,
                                     RevenueTransferStatus transferStatus) {
        return new RevenueResponse(REVENUE_ID, ASSET_ID, "RENT", "PROPERTY_MANAGER", "REF-1",
                gross, expense, fee, "KRW", RECORD_DATE.minusMonths(1), RECORD_DATE, transferStatus);
    }

    private AssetHoldingsSnapshotFetcher.Aggregated aggregated(Long totalHoldingQuantity, List<HoldingItem> items) {
        return new AssetHoldingsSnapshotFetcher.Aggregated(items, totalHoldingQuantity);
    }
}