package com.moneykk.moneytown.settlement.command.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.settlement.command.application.FinalSettlementCommandService;
import com.moneykk.moneytown.settlement.command.application.FinalSettlementDisbursementService;
import com.moneykk.moneytown.settlement.command.controller.api.FinalSettlementCommandApi;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FinalSettlementCommandController implements FinalSettlementCommandApi {

    private final FinalSettlementCommandService finalSettlementCommandService;
    private final FinalSettlementDisbursementService finalSettlementDisbursementService;

    @Override
    @PostMapping("/internal/final-settlements")
    public ResponseEntity<ApiResponse<FinalSettlementBatchResponse>> openFinalSettlement(
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @Valid @RequestBody OpenFinalSettlementRequest request) {
        FinalSettlementBatchResponse response = finalSettlementCommandService.openFinalSettlement(role, request);
        // status==CALCULATED만으로는 "방금 생성됨"과 "이미 존재하지만 아직 disburse()가 markDisbursing()을 못 돌린 기존 배치 구분X
        // 첫 요청 직후 재요청(멱등 재시도)이 짧은 창을 파고들면 같은 회차의 지급이 두 번 트리거 가능 -> 서비스가 명시적으로 내려주는 newlyCreated로만 판단한다.
        if (response.newlyCreated()) {
            finalSettlementDisbursementService.disburseAsync(response.finalSettlementBatchId());
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "최종 정산 회차가 개시되었습니다."));
    }

    @Override
    @PostMapping("/final-settlements/{finalSettlementBatchId}/retry")
    public ResponseEntity<ApiResponse<FinalSettlementRetryResponse>> retryFinalSettlement(
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @PathVariable UUID finalSettlementBatchId,
            @RequestBody(required = false) FinalSettlementRetryRequest request) {
        FinalSettlementRetryResponse response = finalSettlementCommandService.retryFinalSettlement(
                role, finalSettlementBatchId, request != null ? request : new FinalSettlementRetryRequest(null));
        finalSettlementDisbursementService.disburseAsync(response.finalSettlementBatchId());
        return ResponseEntity.ok(ApiResponse.success(response, "실패 건 재처리가 시작되었습니다."));
    }
}