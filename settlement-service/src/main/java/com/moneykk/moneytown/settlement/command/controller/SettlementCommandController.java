package com.moneykk.moneytown.settlement.command.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.settlement.command.application.DividendDisbursementService;
import com.moneykk.moneytown.settlement.command.application.SettlementCommandService;
import com.moneykk.moneytown.settlement.command.dto.OpenSettlementRequest;
import com.moneykk.moneytown.settlement.command.dto.SettlementBatchResponse;
import com.moneykk.moneytown.settlement.infrastructure.client.RevenueTransferStatusNotifier;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SettlementCommandController {

    private final SettlementCommandService settlementCommandService;
    private final DividendDisbursementService dividendDisbursementService;
    private final RevenueTransferStatusNotifier revenueTransferStatusNotifier;

    @PostMapping("/settlements")
    public ResponseEntity<ApiResponse<SettlementBatchResponse>> openSettlementBatch(
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @Valid @RequestBody OpenSettlementRequest request) {
        SettlementBatchResponse response =
                settlementCommandService.openBatch(role, request.assetId(), request.revenueId());
        revenueTransferStatusNotifier.notifyTransferred(response.revenueId());
        dividendDisbursementService.disburseAsync(response.settlementBatchId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "정산 회차가 개시되었습니다."));
    }

    @PostMapping("/settlements/{settlementBatchId}/retry")
    public ResponseEntity<ApiResponse<SettlementBatchResponse>> retrySettlementBatch(
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @PathVariable UUID settlementBatchId) {
        SettlementBatchResponse response = settlementCommandService.retryBatch(role, settlementBatchId);
        dividendDisbursementService.disburseAsync(response.settlementBatchId());
        return ResponseEntity.ok(ApiResponse.success(response, "정산 회차 재시도가 접수되었습니다."));
    }
}