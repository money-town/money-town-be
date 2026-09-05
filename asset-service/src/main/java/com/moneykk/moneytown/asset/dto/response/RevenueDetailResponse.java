package com.moneykk.moneytown.asset.dto.response;

import com.moneykk.moneytown.asset.entity.Revenue;
import com.moneykk.moneytown.asset.entity.RevenueSourceType;
import com.moneykk.moneytown.asset.entity.RevenueTransferStatus;
import com.moneykk.moneytown.asset.entity.RevenueType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** 정산 서비스에 전달할 수익 조회 응답 */
public record RevenueDetailResponse(
        UUID revenueId,
        UUID assetId,
        RevenueSourceType sourceType,
        String sourceReferenceId,
        RevenueType revenueType,
        BigDecimal grossAmount,
        BigDecimal expenseAmount,
        BigDecimal feeAmount,
        String currency,
        LocalDate periodStart,
        LocalDate periodEnd,
        RevenueTransferStatus transferStatus
) {

    /** Revenue 엔티티를 응답 객체로 변환 */
    public static RevenueDetailResponse from(Revenue revenue) {
        return new RevenueDetailResponse(
                revenue.getId(),
                revenue.getAssetId(),
                revenue.getSourceType(),
                revenue.getSourceReferenceId(),
                revenue.getRevenueType(),
                revenue.getGrossAmount(),
                revenue.getExpenseAmount(),
                revenue.getFeeAmount(),
                revenue.getCurrency(),
                revenue.getPeriodStart(),
                revenue.getPeriodEnd(),
                revenue.getTransferStatus()
        );
    }
}