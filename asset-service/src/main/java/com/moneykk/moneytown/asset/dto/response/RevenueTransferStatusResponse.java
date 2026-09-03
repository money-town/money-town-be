package com.moneykk.moneytown.asset.dto.response;

import com.moneykk.moneytown.asset.entity.Revenue;
import com.moneykk.moneytown.asset.entity.RevenueTransferStatus;

import java.time.Instant;
import java.util.UUID;

/** 수익 전달 상태 변경 응답 */
public record RevenueTransferStatusResponse(
        UUID revenueId,
        RevenueTransferStatus transferStatus,
        Instant transferredAt,
        String failureReason
) {

    /** Revenue 엔티티를 응답 객체로 변환 */
    public static RevenueTransferStatusResponse from(Revenue revenue) {
        return new RevenueTransferStatusResponse(
                revenue.getId(),
                revenue.getTransferStatus(),
                revenue.getTransferredAt(),
                revenue.getFailureReason()
        );
    }
}