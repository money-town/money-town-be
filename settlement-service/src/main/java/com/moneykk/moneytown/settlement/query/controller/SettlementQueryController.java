package com.moneykk.moneytown.settlement.query.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.settlement.query.application.SettlementQueryService;
import com.moneykk.moneytown.settlement.query.dto.SettlementBatchDetailResponse;
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
public class SettlementQueryController {

    private final SettlementQueryService settlementQueryService;

    //TODO: 인가 코드 추가
    @GetMapping("/settlements/{settlementBatchId}")
    public ResponseEntity<ApiResponse<SettlementBatchDetailResponse>> getSettlementBatch(
            @PathVariable UUID settlementBatchId) {
        SettlementBatchDetailResponse response = settlementQueryService.getSettlementBatch(settlementBatchId);
        return ResponseEntity.ok(ApiResponse.success(response, "정산 회차 상태를 조회했습니다."));
    }
}