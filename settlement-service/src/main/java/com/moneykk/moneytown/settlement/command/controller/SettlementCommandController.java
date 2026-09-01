package com.moneykk.moneytown.settlement.command.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.settlement.command.application.DividendDisbursementService;
import com.moneykk.moneytown.settlement.command.application.SettlementCommandService;
import com.moneykk.moneytown.settlement.command.dto.SettlementBatchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SettlementCommandController {

    private final SettlementCommandService settlementCommandService;
    private final DividendDisbursementService dividendDisbursementService;

    //TODO: 인가 코드 추가
    @PostMapping("/assets/{assetId}/dividends/{revenueId}/settle")
    public ResponseEntity<ApiResponse<SettlementBatchResponse>> openSettlementBatch(
            @PathVariable UUID assetId,
            @PathVariable UUID revenueId) {
        SettlementBatchResponse response = settlementCommandService.openBatch(assetId, revenueId);
        dividendDisbursementService.disburseAsync(response.settlementBatchId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "정산 회차가 개시되었습니다."));
    }

    //TODO: 인가 코드 추가
    @PostMapping("/settlements/{settlementBatchId}/retry")
    public ResponseEntity<ApiResponse<SettlementBatchResponse>> retrySettlementBatch(
            @PathVariable UUID settlementBatchId) {
        SettlementBatchResponse response = settlementCommandService.retryBatch(settlementBatchId);
        dividendDisbursementService.disburseAsync(response.settlementBatchId());
        return ResponseEntity.ok(ApiResponse.success(response, "정산 회차 재시도가 접수되었습니다."));
    }
}