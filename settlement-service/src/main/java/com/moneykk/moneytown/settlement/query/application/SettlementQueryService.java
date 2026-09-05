package com.moneykk.moneytown.settlement.query.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.settlement.domain.entity.DividendPayout;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.domain.entity.SettlementBatch;
import com.moneykk.moneytown.settlement.domain.repository.DividendPayoutRepository;
import com.moneykk.moneytown.settlement.domain.repository.SettlementBatchRepository;
import com.moneykk.moneytown.settlement.global.exception.SettlementErrorCode;
import com.moneykk.moneytown.settlement.query.dto.DividendPayoutListItemResponse;
import com.moneykk.moneytown.settlement.query.dto.MyDividendPayoutListItemResponse;
import com.moneykk.moneytown.settlement.query.dto.SettlementBatchDetailResponse;
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
public class SettlementQueryService {

    private final SettlementBatchRepository settlementBatchRepository;
    private final DividendPayoutRepository dividendPayoutRepository;

    @Transactional(readOnly = true)
    public SettlementBatchDetailResponse getSettlementBatch(UUID settlementBatchId) {
        SettlementBatch batch = settlementBatchRepository.findByIdAndIsDeletedFalse(settlementBatchId)
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_BATCH_NOT_FOUND));

        return SettlementBatchDetailResponse.of(batch, buildPayoutSummary(settlementBatchId));
    }

    @Transactional(readOnly = true)
    public PageResponse<DividendPayoutListItemResponse> getPayouts(UUID settlementBatchId, PayoutStatus status, Pageable pageable) {
        if (!settlementBatchRepository.existsByIdAndIsDeletedFalse(settlementBatchId)) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_BATCH_NOT_FOUND);
        }

        Sort sort = status == PayoutStatus.DEAD_LETTER
                ? Sort.by(Sort.Direction.DESC, "retryCount").and(Sort.by(Sort.Direction.ASC, "id"))
                : Sort.by(Sort.Direction.DESC, "amount").and(Sort.by(Sort.Direction.ASC, "id"));
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

        Page<DividendPayout> payouts = status == null
                ? dividendPayoutRepository.findBySettlementBatchIdAndIsDeletedFalse(settlementBatchId, sortedPageable)
                : dividendPayoutRepository.findBySettlementBatchIdAndStatusAndIsDeletedFalse(settlementBatchId, status, sortedPageable);
        return PageResponse.from(payouts, DividendPayoutListItemResponse::of);
    }

    @Transactional(readOnly = true)
    public PageResponse<MyDividendPayoutListItemResponse> getMyDividends(UUID investorId, UUID assetId, Pageable pageable) {
        Pageable unsortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<DividendPayoutRepository.MyDividendPayoutRow> payouts =
                dividendPayoutRepository.findMyDividendPayouts(investorId, assetId, unsortedPageable);
        return PageResponse.from(payouts, MyDividendPayoutListItemResponse::of);
    }

    private SettlementBatchDetailResponse.PayoutSummary buildPayoutSummary(UUID settlementBatchId) {
        Map<PayoutStatus, Long> counts = new EnumMap<>(PayoutStatus.class);
        for (PayoutStatus status : PayoutStatus.values()) {
            counts.put(status, 0L);
        }
        for (DividendPayoutRepository.PayoutStatusCount row : dividendPayoutRepository.countByStatusGrouped(settlementBatchId)) {
            counts.put(row.getStatus(), row.getCount());
        }

        long totalCount = counts.values().stream().mapToLong(Long::longValue).sum();
        long paidCount = counts.get(PayoutStatus.PAID);
        long failedCount = counts.get(PayoutStatus.DEAD_LETTER);
        long pendingCount = counts.get(PayoutStatus.QUEUED) + counts.get(PayoutStatus.PROCESSING) + counts.get(PayoutStatus.RETRYING);

        return new SettlementBatchDetailResponse.PayoutSummary(totalCount, paidCount, failedCount, pendingCount);
    }
}