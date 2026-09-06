package com.moneykk.moneytown.settlement.infrastructure.client.dto;

public record RevenueTransferStatusUpdateRequest(
        RevenueTransferStatus transferStatus,
        String failureReason
) {
}