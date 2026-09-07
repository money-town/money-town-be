package com.moneykk.moneytown.settlement.query.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.query.application.SettlementQueryService;
import com.moneykk.moneytown.settlement.query.controller.api.SettlementQueryApi;
import com.moneykk.moneytown.settlement.query.dto.DividendPayoutListItemResponse;
import com.moneykk.moneytown.settlement.query.dto.MyDividendPayoutListItemResponse;
import com.moneykk.moneytown.settlement.query.dto.SettlementBatchDetailResponse;
import com.moneykk.moneytown.settlement.query.dto.SettlementReconciliationResponse;
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
public class SettlementQueryController implements SettlementQueryApi {

    private final SettlementQueryService settlementQueryService;

    @Override
    @GetMapping("/settlements/{settlementBatchId}")
    public ResponseEntity<ApiResponse<SettlementBatchDetailResponse>> getSettlementBatch(
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @PathVariable UUID settlementBatchId) {
        SettlementBatchDetailResponse response = settlementQueryService.getSettlementBatch(role, settlementBatchId);
        return ResponseEntity.ok(ApiResponse.success(response, "정산 회차 상태를 조회했습니다."));
    }

    @Override
    @GetMapping("/settlements/{settlementBatchId}/payouts")
    public ResponseEntity<ApiResponse<PageResponse<DividendPayoutListItemResponse>>> getPayouts(
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @PathVariable UUID settlementBatchId,
            @RequestParam(required = false) PayoutStatus status,
            @PageableDefault(size = 10) Pageable pageable) {
        PageResponse<DividendPayoutListItemResponse> response = settlementQueryService.getPayouts(role, settlementBatchId, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(response, "회차별 개별 지급 내역을 조회했습니다."));
    }

    @Override
    @GetMapping("/settlements/{settlementBatchId}/reconciliation")
    public ResponseEntity<ApiResponse<SettlementReconciliationResponse>> getReconciliation(
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @PathVariable UUID settlementBatchId) {
        SettlementReconciliationResponse response = settlementQueryService.getReconciliation(role, settlementBatchId);
        return ResponseEntity.ok(ApiResponse.success(response, "배당 총액과 지급 내역의 정합성을 확인했습니다."));
    }

    @Override
    @GetMapping("/dividends/me")
    public ResponseEntity<ApiResponse<PageResponse<MyDividendPayoutListItemResponse>>> getMyDividends(
            @RequestHeader("X-User-Id") UUID investorId,
            @RequestParam(required = false) UUID assetId,
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<MyDividendPayoutListItemResponse> response = settlementQueryService.getMyDividends(investorId, assetId, pageable);
        return ResponseEntity.ok(ApiResponse.success(response, "배당 내역을 조회했습니다."));
    }
}