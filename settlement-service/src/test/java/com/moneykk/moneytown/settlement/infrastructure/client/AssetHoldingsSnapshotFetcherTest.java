package com.moneykk.moneytown.settlement.infrastructure.client;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.HoldingItem;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.HoldingsSnapshotResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetHoldingsSnapshotFetcherTest {

    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final LocalDate AS_OF = LocalDate.of(2026, 9, 1);

    @Mock
    private AssetServiceClient assetServiceClient;

    @InjectMocks
    private AssetHoldingsSnapshotFetcher assetHoldingsSnapshotFetcher;

    @Test
    @DisplayName("단일 페이지면 한 번만 호출하고 그대로 반환한다")
    void fetchesSinglePage() {
        HoldingItem item = new HoldingItem(UUID.randomUUID(), UUID.randomUUID(), 100L);
        when(assetServiceClient.getHoldingsSnapshot(ASSET_ID, AS_OF, null))
                .thenReturn(ApiResponse.success(page(100L, List.of(item), null, false), null));

        AssetHoldingsSnapshotFetcher.Aggregated result = assetHoldingsSnapshotFetcher.fetchAll(ASSET_ID, AS_OF);

        assertThat(result.items()).containsExactly(item);
        assertThat(result.totalHoldingQuantity()).isEqualTo(100L);
    }

    @Test
    @DisplayName("여러 페이지로 나뉘어 오면 cursor를 따라가며 모두 모은다")
    void aggregatesAcrossPaginatedPages() {
        HoldingItem item1 = new HoldingItem(UUID.randomUUID(), UUID.randomUUID(), 1L);
        HoldingItem item2 = new HoldingItem(UUID.randomUUID(), UUID.randomUUID(), 2L);
        when(assetServiceClient.getHoldingsSnapshot(ASSET_ID, AS_OF, null))
                .thenReturn(ApiResponse.success(page(3L, List.of(item1), "cursor-1", true), null));
        when(assetServiceClient.getHoldingsSnapshot(ASSET_ID, AS_OF, "cursor-1"))
                .thenReturn(ApiResponse.success(page(3L, List.of(item2), null, false), null));

        AssetHoldingsSnapshotFetcher.Aggregated result = assetHoldingsSnapshotFetcher.fetchAll(ASSET_ID, AS_OF);

        assertThat(result.items()).containsExactlyInAnyOrder(item1, item2);
        assertThat(result.totalHoldingQuantity()).isEqualTo(3L);
    }

    @Test
    @DisplayName("hasNext=true인데 nextCursor가 null이면 정체로 보고 즉시 예외를 던진다")
    void throwsWhenNextCursorIsNullButHasNextTrue() {
        when(assetServiceClient.getHoldingsSnapshot(eq(ASSET_ID), eq(AS_OF), isNull()))
                .thenReturn(ApiResponse.success(page(10L, List.of(), null, true), null));

        assertThatThrownBy(() -> assetHoldingsSnapshotFetcher.fetchAll(ASSET_ID, AS_OF))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.ASSET_HOLDINGS_PAGINATION_STALLED);
    }

    @Test
    @DisplayName("hasNext=true인데 nextCursor가 직전 요청 cursor와 동일하면 정체로 보고 즉시 예외를 던진다")
    void throwsWhenNextCursorRepeatsPreviousCursor() {
        when(assetServiceClient.getHoldingsSnapshot(ASSET_ID, AS_OF, null))
                .thenReturn(ApiResponse.success(page(10L, List.of(), "cursor-1", true), null));
        when(assetServiceClient.getHoldingsSnapshot(ASSET_ID, AS_OF, "cursor-1"))
                .thenReturn(ApiResponse.success(page(10L, List.of(), "cursor-1", true), null));

        assertThatThrownBy(() -> assetHoldingsSnapshotFetcher.fetchAll(ASSET_ID, AS_OF))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.ASSET_HOLDINGS_PAGINATION_STALLED);
    }

    @Test
    @DisplayName("cursor는 계속 바뀌지만 페이지 수가 상한을 넘으면 예외를 던진다")
    void throwsWhenPageCountExceedsCap() {
        when(assetServiceClient.getHoldingsSnapshot(eq(ASSET_ID), eq(AS_OF), anyString()))
                .thenAnswer(invocation -> {
                    String requestedCursor = invocation.getArgument(2);
                    String nextCursor = requestedCursor + "-next";
                    return ApiResponse.success(page(10L, List.of(), nextCursor, true), null);
                });
        when(assetServiceClient.getHoldingsSnapshot(eq(ASSET_ID), eq(AS_OF), isNull()))
                .thenReturn(ApiResponse.success(page(10L, List.of(), "cursor-0", true), null));

        assertThatThrownBy(() -> assetHoldingsSnapshotFetcher.fetchAll(ASSET_ID, AS_OF))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.ASSET_HOLDINGS_PAGINATION_STALLED);
    }

    @Test
    @DisplayName("items가 null인 페이지는 건너뛰고 계속 진행한다")
    void skipsNullItemsPage() {
        when(assetServiceClient.getHoldingsSnapshot(ASSET_ID, AS_OF, null))
                .thenReturn(ApiResponse.success(new HoldingsSnapshotResponse(ASSET_ID, AS_OF, 5L, null, null, false), null));

        AssetHoldingsSnapshotFetcher.Aggregated result = assetHoldingsSnapshotFetcher.fetchAll(ASSET_ID, AS_OF);

        assertThat(result.items()).isEmpty();
        assertThat(result.totalHoldingQuantity()).isEqualTo(5L);
    }

    private HoldingsSnapshotResponse page(Long totalHoldingQuantity, List<HoldingItem> items, String nextCursor, boolean hasNext) {
        return new HoldingsSnapshotResponse(ASSET_ID, AS_OF, totalHoldingQuantity, items, nextCursor, hasNext);
    }
}