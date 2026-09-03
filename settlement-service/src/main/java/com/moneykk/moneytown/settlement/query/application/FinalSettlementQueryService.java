package com.moneykk.moneytown.settlement.query.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementBatch;
import com.moneykk.moneytown.settlement.domain.entity.FinalSettlementPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.repository.FinalSettlementBatchRepository;
import com.moneykk.moneytown.settlement.domain.repository.FinalSettlementPayoutRepository;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import com.moneykk.moneytown.settlement.query.dto.FinalSettlementBatchDetailResponse;
import com.moneykk.moneytown.settlement.query.dto.FinalSettlementPayoutListItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    @Transactional(readOnly = true)
    public PageResponse<FinalSettlementPayoutListItemResponse> getPayouts(UUID finalSettlementBatchId, PayoutStatus status, Pageable pageable) {
        if (!finalSettlementBatchRepository.existsByIdAndIsDeletedFalse(finalSettlementBatchId)) {
            throw new BusinessException(SettlementErrorCode.FINAL_SETTLEMENT_BATCH_NOT_FOUND);
        }

        Sort sort = status == PayoutStatus.DEAD_LETTER
                ? Sort.by(Sort.Direction.DESC, "retryCount").and(Sort.by(Sort.Direction.ASC, "id"))
                : Sort.by(Sort.Direction.DESC, "amount").and(Sort.by(Sort.Direction.ASC, "id"));
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

        Page<FinalSettlementPayout> payouts = status == null
                ? finalSettlementPayoutRepository.findByFinalSettlementBatchIdAndIsDeletedFalse(finalSettlementBatchId, sortedPageable)
                : finalSettlementPayoutRepository.findByFinalSettlementBatchIdAndStatusAndIsDeletedFalse(finalSettlementBatchId, status, sortedPageable);
        return PageResponse.from(payouts, FinalSettlementPayoutListItemResponse::of);
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