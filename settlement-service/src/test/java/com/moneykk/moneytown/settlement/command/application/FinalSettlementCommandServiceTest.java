package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.settlement.command.dto.FinalSettlementBatchResponse;
import com.moneykk.moneytown.settlement.command.dto.OpenFinalSettlementRequest;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementPayout;
import com.moneykk.moneytown.settlement.domain.entity.SettlementStatus;
import com.moneykk.moneytown.settlement.domain.repository.FinalSettlementBatchRepository;
import com.moneykk.moneytown.settlement.domain.repository.FinalSettlementPayoutRepository;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import com.moneykk.moneytown.settlement.infrastructure.client.AssetServiceClient;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.HoldingItem;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.HoldingsSnapshotResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinalSettlementCommandServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final LocalDate TERMINATED_DATE = LocalDate.of(2026, 8, 31);
    private static final Instant TERMINATED_AT = TERMINATED_DATE.atStartOfDay(SEOUL).toInstant();
    private static final Long UNIT_PRICE = 10_000L;

    @Mock
    private FinalSettlementBatchRepository finalSettlementBatchRepository;
    @Mock
    private FinalSettlementPayoutRepository finalSettlementPayoutRepository;
    @Mock
    private AssetServiceClient assetServiceClient;

    @InjectMocks
    private FinalSettlementCommandService finalSettlementCommandService;

    @Test
    @DisplayName("최종 정산 회차를 정상적으로 개시한다")
    void opensFinalSettlementSuccessfully() {
        stubNoExistingBatch();
        UUID investorId = UUID.randomUUID();
        HoldingsSnapshotResponse page = holdingsPage(100L, List.of(new HoldingItem(UUID.randomUUID(), investorId, 100L)), null, false);
        when(assetServiceClient.getHoldingsSnapshot(ASSET_ID, TERMINATED_DATE, null)).thenReturn(ApiResponse.success(page, null));

        FinalSettlementBatchResponse response = finalSettlementCommandService.openFinalSettlement(request());

        assertThat(response.assetId()).isEqualTo(ASSET_ID);
        assertThat(response.terminatedAt()).isEqualTo(TERMINATED_AT);
        assertThat(response.unitPrice()).isEqualTo(UNIT_PRICE);
        assertThat(response.totalAmount()).isEqualTo(1_000_000L);
        assertThat(response.status()).isEqualTo(SettlementStatus.CALCULATED);
        assertThat(response.payoutCount()).isEqualTo(1);

        ArgumentCaptor<FinalSettlementBatch> batchCaptor = ArgumentCaptor.forClass(FinalSettlementBatch.class);
        verify(finalSettlementBatchRepository).save(batchCaptor.capture());
        assertThat(batchCaptor.getValue().getStatus()).isEqualTo(SettlementStatus.CALCULATED);
        assertThat(batchCaptor.getValue().getTotalAmount()).isEqualTo(1_000_000L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FinalSettlementPayout>> payoutsCaptor = ArgumentCaptor.forClass(List.class);
        verify(finalSettlementPayoutRepository).saveAll(payoutsCaptor.capture());
        assertThat(payoutsCaptor.getValue()).hasSize(1);
        assertThat(payoutsCaptor.getValue().get(0).getInvestorId()).isEqualTo(investorId);
        assertThat(payoutsCaptor.getValue().get(0).getQuantity()).isEqualTo(100L);
        assertThat(payoutsCaptor.getValue().get(0).getAmount()).isEqualTo(1_000_000L);
    }

    @Nested
    @DisplayName("중복 회차 가드")
    class DuplicateBatchGuard {

        @Test
        @DisplayName("자산당 최종 정산 회차는 하나만 존재해야 하므로, 이미 있으면 예외")
        void rejectsWhenBatchAlreadyExistsForAsset() {
            when(finalSettlementBatchRepository.existsByAssetIdAndIsDeletedFalse(ASSET_ID)).thenReturn(true);

            assertThatThrownBy(() -> finalSettlementCommandService.openFinalSettlement(request()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(SettlementErrorCode.FINAL_SETTLEMENT_ALREADY_EXISTS_FOR_ASSET);

            verifyNoInteractions(assetServiceClient, finalSettlementPayoutRepository);
        }
    }

    @Nested
    @DisplayName("조각당 취득가(unitPrice) 검증")
    class UnitPriceValidation {

        @ParameterizedTest(name = "unitPrice={0}")
        @NullSource
        @ValueSource(longs = {0L, -1L})
        @DisplayName("unitPrice가 null이거나 0 이하면 예외")
        void rejectsInvalidUnitPrice(Long invalidUnitPrice) {
            stubNoExistingBatch();

            OpenFinalSettlementRequest request = new OpenFinalSettlementRequest(ASSET_ID, TERMINATED_AT, invalidUnitPrice);

            assertThatThrownBy(() -> finalSettlementCommandService.openFinalSettlement(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(SettlementErrorCode.FINAL_SETTLEMENT_UNIT_PRICE_INVALID);

            verifyNoInteractions(assetServiceClient);
        }
    }

    @Nested
    @DisplayName("보유지분 스냅샷 처리")
    class HoldingsSnapshotHandling {

        @Test
        @DisplayName("전체 보유수량이 유효하지 않으면 예외")
        void rejectsWhenTotalHoldingQuantityInvalid() {
            stubNoExistingBatch();
            HoldingsSnapshotResponse page = holdingsPage(0L, List.of(), null, false);
            when(assetServiceClient.getHoldingsSnapshot(ASSET_ID, TERMINATED_DATE, null)).thenReturn(ApiResponse.success(page, null));

            assertThatThrownBy(() -> finalSettlementCommandService.openFinalSettlement(request()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(SettlementErrorCode.HOLDING_SNAPSHOT_INVALID);

            verify(finalSettlementBatchRepository, never()).save(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("여러 페이지로 나뉘어 오면 모두 모아서 반영한다")
        void aggregatesAcrossPaginatedPages() {
            stubNoExistingBatch();
            UUID investor1 = UUID.randomUUID();
            UUID investor2 = UUID.randomUUID();
            HoldingsSnapshotResponse firstPage = holdingsPage(3L, List.of(new HoldingItem(UUID.randomUUID(), investor1, 1L)), "cursor-1", true);
            HoldingsSnapshotResponse secondPage = holdingsPage(3L, List.of(new HoldingItem(UUID.randomUUID(), investor2, 2L)), null, false);
            when(assetServiceClient.getHoldingsSnapshot(ASSET_ID, TERMINATED_DATE, null)).thenReturn(ApiResponse.success(firstPage, null));
            when(assetServiceClient.getHoldingsSnapshot(ASSET_ID, TERMINATED_DATE, "cursor-1")).thenReturn(ApiResponse.success(secondPage, null));

            FinalSettlementBatchResponse response = finalSettlementCommandService.openFinalSettlement(request());

            assertThat(response.payoutCount()).isEqualTo(2);
            assertThat(response.totalAmount()).isEqualTo(30_000L);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<FinalSettlementPayout>> payoutsCaptor = ArgumentCaptor.forClass(List.class);
            verify(finalSettlementPayoutRepository).saveAll(payoutsCaptor.capture());
            assertThat(payoutsCaptor.getValue())
                    .extracting(FinalSettlementPayout::getInvestorId)
                    .containsExactlyInAnyOrder(investor1, investor2);
        }

        @Test
        @DisplayName("보유수량이 0인 투자자는 지급 대상에서 제외한다")
        void excludesHoldersWithZeroQuantity() {
            stubNoExistingBatch();
            UUID investorWithHolding = UUID.randomUUID();
            UUID investorWithZeroHolding = UUID.randomUUID();
            HoldingsSnapshotResponse page = holdingsPage(100L, List.of(
                    new HoldingItem(UUID.randomUUID(), investorWithHolding, 100L),
                    new HoldingItem(UUID.randomUUID(), investorWithZeroHolding, 0L)
            ), null, false);
            when(assetServiceClient.getHoldingsSnapshot(ASSET_ID, TERMINATED_DATE, null)).thenReturn(ApiResponse.success(page, null));

            FinalSettlementBatchResponse response = finalSettlementCommandService.openFinalSettlement(request());

            assertThat(response.payoutCount()).isEqualTo(1);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<FinalSettlementPayout>> payoutsCaptor = ArgumentCaptor.forClass(List.class);
            verify(finalSettlementPayoutRepository).saveAll(payoutsCaptor.capture());
            assertThat(payoutsCaptor.getValue())
                    .extracting(FinalSettlementPayout::getInvestorId)
                    .containsExactly(investorWithHolding);
        }
    }

    @Nested
    @DisplayName("최종 정산 총액 계산")
    class TotalAmountRules {

        @Test
        @DisplayName("모든 보유수량이 0이라 총액이 0원 이하면 예외")
        void rejectsWhenTotalAmountNotPositive() {
            stubNoExistingBatch();
            HoldingsSnapshotResponse page = holdingsPage(100L, List.of(
                    new HoldingItem(UUID.randomUUID(), UUID.randomUUID(), 0L)
            ), null, false);
            when(assetServiceClient.getHoldingsSnapshot(ASSET_ID, TERMINATED_DATE, null)).thenReturn(ApiResponse.success(page, null));

            assertThatThrownBy(() -> finalSettlementCommandService.openFinalSettlement(request()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(SettlementErrorCode.FINAL_SETTLEMENT_AMOUNT_NOT_POSITIVE);

            verifyNoInteractions(finalSettlementPayoutRepository);
        }
    }

    private void stubNoExistingBatch() {
        when(finalSettlementBatchRepository.existsByAssetIdAndIsDeletedFalse(ASSET_ID)).thenReturn(false);
    }

    private OpenFinalSettlementRequest request() {
        return new OpenFinalSettlementRequest(ASSET_ID, TERMINATED_AT, UNIT_PRICE);
    }

    private HoldingsSnapshotResponse holdingsPage(Long totalHoldingQuantity, List<HoldingItem> items, String nextCursor, boolean hasNext) {
        return new HoldingsSnapshotResponse(ASSET_ID, TERMINATED_DATE, totalHoldingQuantity, items, nextCursor, hasNext);
    }
}