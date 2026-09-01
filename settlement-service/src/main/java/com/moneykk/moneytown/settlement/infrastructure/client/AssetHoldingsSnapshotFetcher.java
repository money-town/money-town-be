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

    private final AssetServiceClient assetServiceClient;

    public Aggregated fetchAll(UUID assetId, LocalDate asOf) {
        List<HoldingItem> allItems = new ArrayList<>();
        Long totalHoldingQuantity = null;
        String cursor = null;
        boolean hasNext = true;
        int pageCount = 0;

        while (hasNext) {
            if (++pageCount > MAX_PAGES) {
                throw new BusinessException(SettlementErrorCode.ASSET_HOLDINGS_PAGINATION_STALLED);
            }

            String requestCursor = cursor;
            HoldingsSnapshotResponse page = FeignExceptionTranslator.call(
                    () -> assetServiceClient.getHoldingsSnapshot(assetId, asOf, requestCursor).data(),
                    SettlementErrorCode.ASSET_HOLDINGS_NOT_FOUND);

            if (page.items() != null) {
                allItems.addAll(page.items());
            }
            totalHoldingQuantity = page.totalHoldingQuantity();
            hasNext = page.hasNext();

            String nextCursor = page.nextCursor();
            if (hasNext && Objects.equals(nextCursor, requestCursor)) {
                throw new BusinessException(SettlementErrorCode.ASSET_HOLDINGS_PAGINATION_STALLED);
            }
            cursor = nextCursor;
        }

        return new Aggregated(allItems, totalHoldingQuantity);
    }

    public record Aggregated(List<HoldingItem> items, Long totalHoldingQuantity) {
    }
}