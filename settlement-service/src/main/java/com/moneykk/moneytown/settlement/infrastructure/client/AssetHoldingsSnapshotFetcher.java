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
        UUID cursor = null;
        boolean hasNext = true;
        int pageCount = 0;

        while (hasNext) {
            if (++pageCount > MAX_PAGES) {
                throw new BusinessException(SettlementErrorCode.ASSET_HOLDINGS_PAGINATION_STALLED);
            }

            UUID requestCursor = cursor;
            HoldingsSnapshotResponse page = FeignExceptionTranslator.call(
                    () -> assetServiceClient.getHoldingsSnapshot(SYSTEM_ROLE, assetId, asOf, requestCursor).data(),
                    SettlementErrorCode.ASSET_HOLDINGS_NOT_FOUND);

            if (page.holdings() != null) {
                allItems.addAll(page.holdings());
            }
            hasNext = page.hasNext();

            UUID nextCursor = page.nextCursor();
            if (hasNext && Objects.equals(nextCursor, requestCursor)) {
                throw new BusinessException(SettlementErrorCode.ASSET_HOLDINGS_PAGINATION_STALLED);
            }
            cursor = nextCursor;
        }

        // 자산 서비스가 페이지마다 totalHoldingQuantity를 함께 내려주지만,
        // 여러 페이지에 걸쳐 모은 항목 수량 합계를 정산 서비스가 직접 재계산해 값을 신뢰한다.
        long totalHoldingQuantity = allItems.stream()
                .mapToLong(item -> item.quantity() == null ? 0L : item.quantity())
                .sum();

        return new Aggregated(allItems, totalHoldingQuantity);
    }

    public record Aggregated(List<HoldingItem> items, Long totalHoldingQuantity) {
    }
}