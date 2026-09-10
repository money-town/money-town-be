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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetHoldingsSnapshotFetcherTest {

    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final LocalDate AS_OF = LocalDate.of(2026, 9, 1);
    // Feign 클라이언트는 로케일 종속 직렬화를 피하려고 asOf를 String으로 받는다 (AssetHoldingsSnapshotFetcher 참고)
    private static final String AS_OF_ISO = AS_OF.format(DateTimeFormatter.ISO_LOCAL_DATE);

    @Mock
    private AssetServiceClient assetServiceClient;

    @InjectMocks
    private AssetHoldingsSnapshotFetcher assetHoldingsSnapshotFetcher;

    @Test
    @DisplayName("단일 페이지면 한 번만 호출하고, 지분 수량 합계는 직접 계산한다")
    void fetchesSinglePage() {
        HoldingItem item = new HoldingItem(UUID.randomUUID(), UUID.randomUUID(), 100L);
        when(assetServiceClient.getHoldingsSnapshot("SYSTEM", ASSET_ID, AS_OF_ISO, null))
                .thenReturn(ApiResponse.success(page(List.of(item), 100L, null, false), null));

        AssetHoldingsSnapshotFetcher.Aggregated result = assetHoldingsSnapshotFetcher.fetchAll(ASSET_ID, AS_OF);

        assertThat(result.items()).containsExactly(item);
        assertThat(result.totalHoldingQuantity()).isEqualTo(100L);
    }

    @Test
    @DisplayName("여러 페이지로 나뉘어 오면 cursor를 따라가며 모두 모으고, 전체 페이지의 수량을 합산한다")
    void aggregatesAcrossPaginatedPages() {
        HoldingItem item1 = new HoldingItem(UUID.randomUUID(), UUID.randomUUID(), 1L);
        HoldingItem item2 = new HoldingItem(UUID.randomUUID(), UUID.randomUUID(), 2L);
        UUID cursor1 = UUID.randomUUID();
        when(assetServiceClient.getHoldingsSnapshot("SYSTEM", ASSET_ID, AS_OF_ISO, null))
                .thenReturn(ApiResponse.success(page(List.of(item1), 3L, cursor1, true), null));
        when(assetServiceClient.getHoldingsSnapshot("SYSTEM", ASSET_ID, AS_OF_ISO, cursor1))
                .thenReturn(ApiResponse.success(page(List.of(item2), 3L, null, false), null));

        AssetHoldingsSnapshotFetcher.Aggregated result = assetHoldingsSnapshotFetcher.fetchAll(ASSET_ID, AS_OF);

        assertThat(result.items()).containsExactlyInAnyOrder(item1, item2);
        assertThat(result.totalHoldingQuantity()).isEqualTo(3L);
    }

    @Test
    @DisplayName("hasNext=true인데 nextCursor가 null이면 정체로 보고 즉시 예외를 던진다")
    void throwsWhenNextCursorIsNullButHasNextTrue() {
        when(assetServiceClient.getHoldingsSnapshot(eq("SYSTEM"), eq(ASSET_ID), eq(AS_OF_ISO), isNull()))
                .thenReturn(ApiResponse.success(page(List.of(), 0L, null, true), null));

        assertThatThrownBy(() -> assetHoldingsSnapshotFetcher.fetchAll(ASSET_ID, AS_OF))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.ASSET_HOLDINGS_PAGINATION_STALLED);
    }

    @Test
    @DisplayName("hasNext=true인데 nextCursor가 직전 요청 cursor와 동일하면 정체로 보고 즉시 예외를 던진다")
    void throwsWhenNextCursorRepeatsPreviousCursor() {
        UUID cursor1 = UUID.randomUUID();
        when(assetServiceClient.getHoldingsSnapshot("SYSTEM", ASSET_ID, AS_OF_ISO, null))
                .thenReturn(ApiResponse.success(page(List.of(), 0L, cursor1, true), null));
        when(assetServiceClient.getHoldingsSnapshot("SYSTEM", ASSET_ID, AS_OF_ISO, cursor1))
                .thenReturn(ApiResponse.success(page(List.of(), 0L, cursor1, true), null));

        assertThatThrownBy(() -> assetHoldingsSnapshotFetcher.fetchAll(ASSET_ID, AS_OF))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.ASSET_HOLDINGS_PAGINATION_STALLED);
    }

    @Test
    @DisplayName("cursor는 계속 바뀌지만 페이지 수가 상한을 넘으면 예외를 던진다")
    void throwsWhenPageCountExceedsCap() {
        when(assetServiceClient.getHoldingsSnapshot(eq("SYSTEM"), eq(ASSET_ID), eq(AS_OF_ISO), nullable(UUID.class)))
                .thenAnswer(invocation -> ApiResponse.success(page(List.of(), 0L, UUID.randomUUID(), true), null));

        assertThatThrownBy(() -> assetHoldingsSnapshotFetcher.fetchAll(ASSET_ID, AS_OF))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.ASSET_HOLDINGS_PAGINATION_STALLED);
    }

    @Test
    @DisplayName("holdings가 null인 페이지는 건너뛰고 계속 진행한다")
    void skipsNullHoldingsPage() {
        when(assetServiceClient.getHoldingsSnapshot("SYSTEM", ASSET_ID, AS_OF_ISO, null))
                .thenReturn(ApiResponse.success(new HoldingsSnapshotResponse(ASSET_ID, AS_OF, 0L, null, null, false), null));

        AssetHoldingsSnapshotFetcher.Aggregated result = assetHoldingsSnapshotFetcher.fetchAll(ASSET_ID, AS_OF);

        assertThat(result.items()).isEmpty();
        assertThat(result.totalHoldingQuantity()).isZero();
    }

    private HoldingsSnapshotResponse page(List<HoldingItem> holdings, long totalHoldingQuantity, UUID nextCursor, boolean hasNext) {
        return new HoldingsSnapshotResponse(ASSET_ID, AS_OF, totalHoldingQuantity, holdings, nextCursor, hasNext);
    }
}