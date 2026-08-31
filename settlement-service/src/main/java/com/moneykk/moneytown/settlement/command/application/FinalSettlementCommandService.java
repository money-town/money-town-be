package com.moneykk.moneytown.settlement.command.application;

import com.moneykk.moneytown.common.client.FeignExceptionTranslator;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.settlement.command.dto.FinalSettlementBatchResponse;
import com.moneykk.moneytown.settlement.command.dto.OpenFinalSettlementRequest;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementPayout;
import com.moneykk.moneytown.settlement.domain.repository.FinalSettlementBatchRepository;
import com.moneykk.moneytown.settlement.domain.repository.FinalSettlementPayoutRepository;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import com.moneykk.moneytown.settlement.infrastructure.client.AssetServiceClient;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.HoldingItem;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.HoldingsSnapshotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinalSettlementCommandService {

    private static final ZoneId SETTLEMENT_ZONE = ZoneId.of("Asia/Seoul");

    private final FinalSettlementBatchRepository finalSettlementBatchRepository;
    private final FinalSettlementPayoutRepository finalSettlementPayoutRepository;
    private final AssetServiceClient assetServiceClient;

    @Transactional
    public FinalSettlementBatchResponse openFinalSettlement(OpenFinalSettlementRequest request) {
        Optional<FinalSettlementBatch> existingBatch =
                finalSettlementBatchRepository.findByAssetIdAndIsDeletedFalse(request.assetId());
        if (existingBatch.isPresent()) {
            return FinalSettlementBatchResponse.of(existingBatch.get());
        }

        LocalDate asOf = request.terminatedAt().atZone(SETTLEMENT_ZONE).toLocalDate();
        List<HoldingItem> holders = fetchHolders(request.assetId(), asOf);
        if (holders.isEmpty()) {
            throw new BusinessException(SettlementErrorCode.FINAL_SETTLEMENT_HOLDERS_NOT_FOUND);
        }

        long totalAmount = holders.stream()
                .mapToLong(holder -> holder.quantity() * request.unitPrice())
                .sum();

        FinalSettlementBatch batch = FinalSettlementBatch.open(
                request.assetId(), request.terminatedAt(), request.unitPrice(), totalAmount);
        batch.markCalculated();

        List<FinalSettlementPayout> payouts = holders.stream()
                .map(holder -> FinalSettlementPayout.queue(
                        batch.getId(), holder.userId(), holder.quantity(), holder.quantity() * request.unitPrice()))
                .toList();

        finalSettlementBatchRepository.save(batch);
        finalSettlementPayoutRepository.saveAll(payouts);

        return FinalSettlementBatchResponse.of(batch);
    }

    private List<HoldingItem> fetchHolders(UUID assetId, LocalDate asOf) {
        List<HoldingItem> allItems = new ArrayList<>();
        String cursor = null;
        boolean hasNext = true;

        while (hasNext) {
            String requestCursor = cursor;
            HoldingsSnapshotResponse page = FeignExceptionTranslator.call(
                    () -> assetServiceClient.getHoldingsSnapshot(assetId, asOf, requestCursor).data(),
                    SettlementErrorCode.ASSET_HOLDINGS_NOT_FOUND);

            if (page.items() != null) {
                allItems.addAll(page.items());
            }
            hasNext = page.hasNext();
            cursor = page.nextCursor();
        }

        return allItems.stream()
                .filter(item -> item.quantity() != null && item.quantity() > 0)
                .toList();
    }
}