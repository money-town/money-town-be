package com.moneykk.moneytown.settlement.query.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.domain.repository.DividendPayoutRepository;
import com.moneykk.moneytown.settlement.domain.repository.SettlementBatchRepository;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import com.moneykk.moneytown.settlement.query.dto.SettlementBatchDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettlementQueryService {

    private final SettlementBatchRepository settlementBatchRepository;
    private final DividendPayoutRepository dividendPayoutRepository;

    @Transactional(readOnly = true)
    public SettlementBatchDetailResponse getSettlementBatch(UUID settlementBatchId) {
        SettlementBatch batch = settlementBatchRepository.findByIdAndIsDeletedFalse(settlementBatchId)
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_BATCH_NOT_FOUND));

        return SettlementBatchDetailResponse.of(batch, buildPayoutSummary(settlementBatchId));
    }

    private SettlementBatchDetailResponse.PayoutSummary buildPayoutSummary(UUID settlementBatchId) {
        Map<PayoutStatus, Integer> counts = new EnumMap<>(PayoutStatus.class);
        for (PayoutStatus status : PayoutStatus.values()) {
            counts.put(status, 0);
        }
        for (DividendPayoutRepository.PayoutStatusCount row : dividendPayoutRepository.countByStatusGrouped(settlementBatchId)) {
            counts.put(row.getStatus(), (int) row.getCount());
        }

        int totalCount = counts.values().stream().mapToInt(Integer::intValue).sum();
        int paidCount = counts.get(PayoutStatus.PAID);
        int failedCount = counts.get(PayoutStatus.DEAD_LETTER);
        int pendingCount = counts.get(PayoutStatus.QUEUED) + counts.get(PayoutStatus.PROCESSING) + counts.get(PayoutStatus.RETRYING);

        return new SettlementBatchDetailResponse.PayoutSummary(totalCount, paidCount, failedCount, pendingCount);
    }
}