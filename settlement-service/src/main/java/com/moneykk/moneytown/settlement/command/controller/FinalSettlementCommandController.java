package com.moneykk.moneytown.settlement.command.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.settlement.command.application.FinalSettlementCommandService;
import com.moneykk.moneytown.settlement.command.application.FinalSettlementDisbursementService;
import com.moneykk.moneytown.settlement.command.dto.FinalSettlementBatchResponse;
import com.moneykk.moneytown.settlement.command.dto.FinalSettlementRetryRequest;
import com.moneykk.moneytown.settlement.command.dto.FinalSettlementRetryResponse;
import com.moneykk.moneytown.settlement.command.dto.OpenFinalSettlementRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FinalSettlementCommandController {

    private final FinalSettlementCommandService finalSettlementCommandService;
    private final FinalSettlementDisbursementService finalSettlementDisbursementService;

    //TODO: 인가 코드 추가 (SYSTEM 권한)
    @PostMapping("/internal/final-settlements")
    public ResponseEntity<ApiResponse<FinalSettlementBatchResponse>> openFinalSettlement(
            @Valid @RequestBody OpenFinalSettlementRequest request) {
        FinalSettlementBatchResponse response = finalSettlementCommandService.openFinalSettlement(request);
        finalSettlementDisbursementService.disburseAsync(response.finalSettlementBatchId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "최종 정산 회차가 개시되었습니다."));
    }

    //TODO: 인가 코드 추가 (ADMIN 권한)
    @PostMapping("/final-settlements/{finalSettlementBatchId}/retry")
    public ResponseEntity<ApiResponse<FinalSettlementRetryResponse>> retryFinalSettlement(
            @PathVariable UUID finalSettlementBatchId,
            @RequestBody(required = false) FinalSettlementRetryRequest request) {
        FinalSettlementRetryResponse response = finalSettlementCommandService.retryFinalSettlement(
                finalSettlementBatchId, request != null ? request : new FinalSettlementRetryRequest(null));
        finalSettlementDisbursementService.disburseAsync(response.finalSettlementBatchId());
        return ResponseEntity.ok(ApiResponse.success(response, "실패 건 재처리가 시작되었습니다."));
    }
}