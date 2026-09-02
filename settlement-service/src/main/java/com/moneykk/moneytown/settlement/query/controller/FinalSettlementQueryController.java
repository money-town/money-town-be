package com.moneykk.moneytown.settlement.query.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.settlement.query.application.FinalSettlementQueryService;
import com.moneykk.moneytown.settlement.query.dto.FinalSettlementBatchDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FinalSettlementQueryController {

    private final FinalSettlementQueryService finalSettlementQueryService;

    //TODO: 인가 코드 추가
    @GetMapping("/final-settlements/{finalSettlementBatchId}")
    public ResponseEntity<ApiResponse<FinalSettlementBatchDetailResponse>> getFinalSettlementBatch(
            @PathVariable UUID finalSettlementBatchId) {
        FinalSettlementBatchDetailResponse response = finalSettlementQueryService.getFinalSettlementBatch(finalSettlementBatchId);
        return ResponseEntity.ok(ApiResponse.success(response, "최종 정산 회차 상태를 조회했습니다."));
    }
}