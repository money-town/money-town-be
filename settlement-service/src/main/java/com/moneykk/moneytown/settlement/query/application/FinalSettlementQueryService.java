package com.moneykk.moneytown.settlement.query.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.repository.FinalSettlementBatchRepository;
import com.moneykk.moneytown.settlement.domain.repository.FinalSettlementPayoutRepository;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import com.moneykk.moneytown.settlement.query.dto.FinalSettlementBatchDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinalSettlementQueryService {

    private final FinalSettlementBatchRepository finalSettlementBatchRepository;
    private final FinalSettlementPayoutRepository finalSettlementPayoutRepository;

    @Transactional(readOnly = true)
    public FinalSettlementBatchDetailResponse getFinalSettlementBatch(UUID finalSettlementBatchId) {
        FinalSettlementBatch batch = finalSettlementBatchRepository.findByIdAndIsDeletedFalse(finalSettlementBatchId)
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.FINAL_SETTLEMENT_BATCH_NOT_FOUND));

        return FinalSettlementBatchDetailResponse.of(batch, buildProgress(finalSettlementBatchId));
    }

    private FinalSettlementBatchDetailResponse.Progress buildProgress(UUID finalSettlementBatchId) {
        Map<PayoutStatus, Long> counts = new EnumMap<>(PayoutStatus.class);
        for (PayoutStatus status : PayoutStatus.values()) {
            counts.put(status, 0L);
        }
        for (FinalSettlementPayoutRepository.PayoutStatusCount row : finalSettlementPayoutRepository.countByStatusGrouped(finalSettlementBatchId)) {
            counts.put(row.getStatus(), row.getCount());
        }

        long totalCount = counts.values().stream().mapToLong(Long::longValue).sum();
        long paidCount = counts.get(PayoutStatus.PAID);
        long failedCount = counts.get(PayoutStatus.DEAD_LETTER);
        long pendingCount = counts.get(PayoutStatus.QUEUED) + counts.get(PayoutStatus.PROCESSING) + counts.get(PayoutStatus.RETRYING);

        return new FinalSettlementBatchDetailResponse.Progress(totalCount, paidCount, failedCount, pendingCount);
    }
}