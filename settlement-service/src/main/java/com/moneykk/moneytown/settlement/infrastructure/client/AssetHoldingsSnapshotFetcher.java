package com.moneykk.moneytown.settlement.infrastructure.client;

import com.moneykk.moneytown.common.client.FeignExceptionTranslator;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.HoldingItem;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.HoldingsSnapshotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AssetHoldingsSnapshotFetcher {

    private static final int MAX_PAGES = 1000;
    private static final String SYSTEM_ROLE = "SYSTEM";

    private final AssetServiceClient assetServiceClient;

    public Aggregated fetchAll(UUID assetId, LocalDate asOf) {
        List<HoldingItem> allItems = new ArrayList<>();
        String cursor = null;
        boolean hasNext = true;
        int pageCount = 0;

        while (hasNext) {
            if (++pageCount > MAX_PAGES) {
                throw new BusinessException(SettlementErrorCode.ASSET_HOLDINGS_PAGINATION_STALLED);
            }

            String requestCursor = cursor;
            HoldingsSnapshotResponse page = FeignExceptionTranslator.call(
                    () -> assetServiceClient.getHoldingsSnapshot(SYSTEM_ROLE, assetId, asOf, requestCursor).data(),
                    SettlementErrorCode.ASSET_HOLDINGS_NOT_FOUND);

            if (page.holdings() != null) {
                allItems.addAll(page.holdings());
            }
            hasNext = page.hasNext();

            String nextCursor = page.nextCursor();
            if (hasNext && Objects.equals(nextCursor, requestCursor)) {
                throw new BusinessException(SettlementErrorCode.ASSET_HOLDINGS_PAGINATION_STALLED);
            }
            cursor = nextCursor;
        }

        // 자산 서비스가 전체 발행 지분 수량을 내려주지 않아, 모든 페이지를 합친 뒤 직접 합산한다.
        long totalHoldingQuantity = allItems.stream()
                .mapToLong(item -> item.quantity() == null ? 0L : item.quantity())
                .sum();

        return new Aggregated(allItems, totalHoldingQuantity);
    }

    public record Aggregated(List<HoldingItem> items, Long totalHoldingQuantity) {
    }
}