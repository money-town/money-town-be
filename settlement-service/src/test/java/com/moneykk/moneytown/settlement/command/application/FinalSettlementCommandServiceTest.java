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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private static final Long UNIT_PRICE = 1_000_000L;

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
        HoldingsSnapshotResponse page = holdingsPage(List.of(new HoldingItem(UUID.randomUUID(), investorId, 900L)), null, false);
        when(assetServiceClient.getHoldingsSnapshot(ASSET_ID, TERMINATED_DATE, null)).thenReturn(ApiResponse.success(page, null));

        FinalSettlementBatchResponse response = finalSettlementCommandService.openFinalSettlement(request());

        assertThat(response.assetId()).isEqualTo(ASSET_ID);
        assertThat(response.totalAmount()).isEqualTo(900_000_000L);
        assertThat(response.status()).isEqualTo(SettlementStatus.CALCULATED);

        ArgumentCaptor<FinalSettlementBatch> batchCaptor = ArgumentCaptor.forClass(FinalSettlementBatch.class);
        verify(finalSettlementBatchRepository).save(batchCaptor.capture());
        assertThat(batchCaptor.getValue().getStatus()).isEqualTo(SettlementStatus.CALCULATED);
        assertThat(batchCaptor.getValue().getAssetId()).isEqualTo(ASSET_ID);
        assertThat(batchCaptor.getValue().getTerminatedAt()).isEqualTo(TERMINATED_AT);
        assertThat(batchCaptor.getValue().getUnitPrice()).isEqualTo(UNIT_PRICE);
        assertThat(batchCaptor.getValue().getTotalAmount()).isEqualTo(900_000_000L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FinalSettlementPayout>> payoutsCaptor = ArgumentCaptor.forClass(List.class);
        verify(finalSettlementPayoutRepository).saveAll(payoutsCaptor.capture());
        assertThat(payoutsCaptor.getValue()).hasSize(1);
        assertThat(payoutsCaptor.getValue().get(0).getInvestorId()).isEqualTo(investorId);
        assertThat(payoutsCaptor.getValue().get(0).getQuantity()).isEqualTo(900L);
        assertThat(payoutsCaptor.getValue().get(0).getAmount()).isEqualTo(900_000_000L);
    }

    @Nested
    @DisplayName("중복 호출(멱등) 처리")
    class DuplicateCallIdempotency {

        @Test
        @DisplayName("이미 해당 자산으로 개시된 최종 정산 회차가 있으면, 새로 만들지 않고 기존 회차를 그대로 반환한다")
        void returnsExistingBatchWithoutCreatingNew() {
            FinalSettlementBatch existingBatch = FinalSettlementBatch.open(ASSET_ID, TERMINATED_AT, UNIT_PRICE, 900_000_000L);
            existingBatch.markCalculated();
            when(finalSettlementBatchRepository.findByAssetIdAndIsDeletedFalse(ASSET_ID)).thenReturn(Optional.of(existingBatch));

            FinalSettlementBatchResponse response = finalSettlementCommandService.openFinalSettlement(request());

            assertThat(response.finalSettlementBatchId()).isEqualTo(existingBatch.getId());
            assertThat(response.assetId()).isEqualTo(ASSET_ID);
            assertThat(response.totalAmount()).isEqualTo(900_000_000L);
            assertThat(response.status()).isEqualTo(SettlementStatus.CALCULATED);

            verify(finalSettlementBatchRepository, never()).save(any());
            verifyNoInteractions(assetServiceClient, finalSettlementPayoutRepository);
        }
    }

    @Nested
    @DisplayName("보유지분 스냅샷 처리")
    class HoldingsSnapshotHandling {

        @Test
        @DisplayName("여러 페이지로 나뉘어 오면 모두 모아서 반영한다")
        void aggregatesAcrossPaginatedPages() {
            stubNoExistingBatch();
            UUID investor1 = UUID.randomUUID();
            UUID investor2 = UUID.randomUUID();
            HoldingsSnapshotResponse firstPage = holdingsPage(List.of(new HoldingItem(UUID.randomUUID(), investor1, 100L)), "cursor-1", true);
            HoldingsSnapshotResponse secondPage = holdingsPage(List.of(new HoldingItem(UUID.randomUUID(), investor2, 200L)), null, false);
            when(assetServiceClient.getHoldingsSnapshot(ASSET_ID, TERMINATED_DATE, null)).thenReturn(ApiResponse.success(firstPage, null));
            when(assetServiceClient.getHoldingsSnapshot(ASSET_ID, TERMINATED_DATE, "cursor-1")).thenReturn(ApiResponse.success(secondPage, null));

            FinalSettlementBatchResponse response = finalSettlementCommandService.openFinalSettlement(request());

            assertThat(response.totalAmount()).isEqualTo(300_000_000L);

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
            HoldingsSnapshotResponse page = holdingsPage(List.of(
                    new HoldingItem(UUID.randomUUID(), investorWithHolding, 900L),
                    new HoldingItem(UUID.randomUUID(), investorWithZeroHolding, 0L)
            ), null, false);
            when(assetServiceClient.getHoldingsSnapshot(ASSET_ID, TERMINATED_DATE, null)).thenReturn(ApiResponse.success(page, null));

            finalSettlementCommandService.openFinalSettlement(request());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<FinalSettlementPayout>> payoutsCaptor = ArgumentCaptor.forClass(List.class);
            verify(finalSettlementPayoutRepository).saveAll(payoutsCaptor.capture());
            assertThat(payoutsCaptor.getValue())
                    .extracting(FinalSettlementPayout::getInvestorId)
                    .containsExactly(investorWithHolding);
        }
    }

    @Nested
    @DisplayName("보유자 검증")
    class HoldersValidation {

        @Test
        @DisplayName("terminatedAt 시점 보유자가 없으면 예외")
        void rejectsWhenNoHoldersExist() {
            stubNoExistingBatch();
            HoldingsSnapshotResponse page = holdingsPage(List.of(), null, false);
            when(assetServiceClient.getHoldingsSnapshot(ASSET_ID, TERMINATED_DATE, null)).thenReturn(ApiResponse.success(page, null));

            assertThatThrownBy(() -> finalSettlementCommandService.openFinalSettlement(request()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(SettlementErrorCode.FINAL_SETTLEMENT_HOLDERS_NOT_FOUND);

            verify(finalSettlementBatchRepository, never()).save(any());
            verifyNoInteractions(finalSettlementPayoutRepository);
        }

        @Test
        @DisplayName("모든 항목의 보유수량이 0이면 보유자가 없는 것으로 취급해 예외")
        void rejectsWhenAllHoldingsAreZero() {
            stubNoExistingBatch();
            HoldingsSnapshotResponse page = holdingsPage(List.of(
                    new HoldingItem(UUID.randomUUID(), UUID.randomUUID(), 0L)
            ), null, false);
            when(assetServiceClient.getHoldingsSnapshot(ASSET_ID, TERMINATED_DATE, null)).thenReturn(ApiResponse.success(page, null));

            assertThatThrownBy(() -> finalSettlementCommandService.openFinalSettlement(request()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(SettlementErrorCode.FINAL_SETTLEMENT_HOLDERS_NOT_FOUND);

            verifyNoInteractions(finalSettlementPayoutRepository);
        }
    }

    private void stubNoExistingBatch() {
        when(finalSettlementBatchRepository.findByAssetIdAndIsDeletedFalse(ASSET_ID)).thenReturn(Optional.empty());
    }

    private OpenFinalSettlementRequest request() {
        return new OpenFinalSettlementRequest(ASSET_ID, TERMINATED_AT, UNIT_PRICE);
    }

    private HoldingsSnapshotResponse holdingsPage(List<HoldingItem> items, String nextCursor, boolean hasNext) {
        long totalHoldingQuantity = items.stream().mapToLong(HoldingItem::quantity).sum();
        return new HoldingsSnapshotResponse(ASSET_ID, TERMINATED_DATE, totalHoldingQuantity, items, nextCursor, hasNext);
    }
}