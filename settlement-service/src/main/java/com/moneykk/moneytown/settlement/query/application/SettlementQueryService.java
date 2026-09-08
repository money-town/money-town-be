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
import com.moneykk.moneytown.settlement.query.dto.SettlementReconciliationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettlementQueryService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final SettlementBatchRepository settlementBatchRepository;
    private final DividendPayoutRepository dividendPayoutRepository;

    @Transactional(readOnly = true)
    public SettlementBatchDetailResponse getSettlementBatch(String role, UUID settlementBatchId) {
        validateAdmin(role);
        SettlementBatch batch = settlementBatchRepository.findByIdAndIsDeletedFalse(settlementBatchId)
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_BATCH_NOT_FOUND));

        return SettlementBatchDetailResponse.of(batch, buildPayoutSummary(settlementBatchId));
    }

    @Transactional(readOnly = true)
    public PageResponse<DividendPayoutListItemResponse> getPayouts(String role, UUID settlementBatchId, PayoutStatus status, Pageable pageable) {
        validateAdmin(role);
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

    @Transactional(readOnly = true)
    public SettlementReconciliationResponse getReconciliation(String role, UUID settlementBatchId) {
        validateAdmin(role);
        SettlementBatch batch = settlementBatchRepository.findByIdAndIsDeletedFalse(settlementBatchId)
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_BATCH_NOT_FOUND));

        List<DividendPayout> payouts = dividendPayoutRepository.findBySettlementBatchIdAndIsDeletedFalse(settlementBatchId);

        long expectedAmount = batch.getTotalAmount() - batch.getRemainderAmount();
        long totalPayoutAmount = payouts.stream().mapToLong(DividendPayout::getAmount).sum();
        long paidAmount = payouts.stream()
                .filter(payout -> payout.getStatus() == PayoutStatus.PAID)
                .mapToLong(DividendPayout::getAmount)
                .sum();

        return SettlementReconciliationResponse.of(settlementBatchId, expectedAmount, totalPayoutAmount, paidAmount);
    }

    private void validateAdmin(String role) {
        if (!ADMIN_ROLE.equals(role)) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_ACCESS_DENIED);
        }
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