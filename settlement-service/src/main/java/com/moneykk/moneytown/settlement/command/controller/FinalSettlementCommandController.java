package com.moneykk.moneytown.settlement.command.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.settlement.command.application.FinalSettlementCommandService;
import com.moneykk.moneytown.settlement.command.application.FinalSettlementDisbursementService;
import com.moneykk.moneytown.settlement.command.dto.FinalSettlementBatchResponse;
import com.moneykk.moneytown.settlement.command.dto.OpenFinalSettlementRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal")
@RequiredArgsConstructor
public class FinalSettlementCommandController {

    private final FinalSettlementCommandService finalSettlementCommandService;
    private final FinalSettlementDisbursementService finalSettlementDisbursementService;

    //TODO: 인가 코드 추가 (SYSTEM 권한)
    @PostMapping("/final-settlements")
    public ResponseEntity<ApiResponse<FinalSettlementBatchResponse>> openFinalSettlement(
            @RequestBody OpenFinalSettlementRequest request) {
        FinalSettlementBatchResponse response = finalSettlementCommandService.openFinalSettlement(request);
        finalSettlementDisbursementService.disburseAsync(response.finalSettlementBatchId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "최종 정산 회차가 개시되었습니다."));
    }
}