package com.moneykk.moneytown.settlement.query.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.settlement.query.application.SettlementQueryService;
import com.moneykk.moneytown.settlement.query.dto.DividendPayoutListItemResponse;
import com.moneykk.moneytown.settlement.query.dto.SettlementBatchDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SettlementQueryController {

    private final SettlementQueryService settlementQueryService;

    //TODO: 인가 코드 추가
    @GetMapping("/settlements/{settlementBatchId}")
    public ResponseEntity<ApiResponse<SettlementBatchDetailResponse>> getSettlementBatch(
            @PathVariable UUID settlementBatchId) {
        SettlementBatchDetailResponse response = settlementQueryService.getSettlementBatch(settlementBatchId);
        return ResponseEntity.ok(ApiResponse.success(response, "정산 회차 상태를 조회했습니다."));
    }

    //TODO: 인가 코드 추가
    @GetMapping("/settlements/{settlementBatchId}/payouts")
    public ResponseEntity<ApiResponse<PageResponse<DividendPayoutListItemResponse>>> getPayouts(
            @PathVariable UUID settlementBatchId,
            @PageableDefault(size = 10) Pageable pageable) {
        PageResponse<DividendPayoutListItemResponse> response = settlementQueryService.getPayouts(settlementBatchId, pageable);
        return ResponseEntity.ok(ApiResponse.success(response, "회차별 개별 지급 내역을 조회했습니다."));
    }
}