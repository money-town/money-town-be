package com.moneykk.moneytown.settlement.query.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.query.application.FinalSettlementQueryService;
import com.moneykk.moneytown.settlement.query.dto.FinalSettlementBatchDetailResponse;
import com.moneykk.moneytown.settlement.query.dto.FinalSettlementPayoutListItemResponse;
import com.moneykk.moneytown.settlement.query.dto.FinalSettlementReconciliationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FinalSettlementQueryController {

    private final FinalSettlementQueryService finalSettlementQueryService;

    @GetMapping("/final-settlements/{finalSettlementBatchId}")
    public ResponseEntity<ApiResponse<FinalSettlementBatchDetailResponse>> getFinalSettlementBatch(
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @PathVariable UUID finalSettlementBatchId) {
        FinalSettlementBatchDetailResponse response = finalSettlementQueryService.getFinalSettlementBatch(role, finalSettlementBatchId);
        return ResponseEntity.ok(ApiResponse.success(response, "최종 정산 회차 상태를 조회했습니다."));
    }

    @GetMapping("/final-settlements/{finalSettlementBatchId}/payouts")
    public ResponseEntity<ApiResponse<PageResponse<FinalSettlementPayoutListItemResponse>>> getPayouts(
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @PathVariable UUID finalSettlementBatchId,
            @RequestParam(required = false) PayoutStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<FinalSettlementPayoutListItemResponse> response =
                finalSettlementQueryService.getPayouts(role, finalSettlementBatchId, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(response, "개별 반환 내역을 조회했습니다."));
    }

    @GetMapping("/final-settlements/{finalSettlementBatchId}/reconciliation")
    public ResponseEntity<ApiResponse<FinalSettlementReconciliationResponse>> getReconciliation(
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @PathVariable UUID finalSettlementBatchId) {
        FinalSettlementReconciliationResponse response =
                finalSettlementQueryService.getReconciliation(role, finalSettlementBatchId);
        return ResponseEntity.ok(ApiResponse.success(response, "원금반환 총액과 지급 내역의 정합성을 확인했습니다."));
    }
}