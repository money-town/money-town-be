package com.moneykk.moneytown.settlement.command.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.settlement.command.application.FinalSettlementCommandService;
import com.moneykk.moneytown.settlement.command.application.FinalSettlementDisbursementService;
import com.moneykk.moneytown.settlement.command.dto.FinalSettlementBatchResponse;
import com.moneykk.moneytown.settlement.command.dto.FinalSettlementRetryRequest;
import com.moneykk.moneytown.settlement.command.dto.FinalSettlementRetryResponse;
import com.moneykk.moneytown.settlement.command.dto.OpenFinalSettlementRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Final Settlement", description = "최종 정산(원금반환) 개시·재시도 커맨드 API")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FinalSettlementCommandController {

    private final FinalSettlementCommandService finalSettlementCommandService;
    private final FinalSettlementDisbursementService finalSettlementDisbursementService;

    //TODO: 인가 코드 추가 (SYSTEM 권한)
    @Operation(
            tags = "Final Settlement Internal",
            summary = "최종 정산 회차 개시 (자산 서비스 전용 내부 API)",
            description = "자산 서비스가 자산 종료(해지) 확정 시 동기 호출하는 SYSTEM 전용 내부 API다. 종료 시점(terminatedAt) 기준 보유자 스냅샷 조회 "
                    + "→ 보유 수량 × 단가로 총액 계산 → FinalSettlementPayout을 QUEUED 상태로 저장 → 비동기 지급 시작 순서로 처리된다. "
                    + "이미 해당 자산으로 개시된 회차가 있으면 새로 만들지 않고 기존 회차를 그대로 반환한다(멱등)."
    )
    @PostMapping("/internal/final-settlements")
    public ResponseEntity<ApiResponse<FinalSettlementBatchResponse>> openFinalSettlement(
            @Valid @RequestBody OpenFinalSettlementRequest request) {
        FinalSettlementBatchResponse response = finalSettlementCommandService.openFinalSettlement(request);
        finalSettlementDisbursementService.disburseAsync(response.finalSettlementBatchId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "최종 정산 회차가 개시되었습니다."));
    }

    @Operation(
            summary = "최종 정산 실패 건 재처리",
            description = "ADMIN 권한으로 FAILED/PARTIAL_FAILED 상태인 최종 정산 회차의 실패 건을 재처리한다. "
                    + "finalSettlementPayoutIds를 지정하지 않으면 회차의 DEAD_LETTER 건 전체를, 지정하면 그 건들만 선택적으로 QUEUED로 되돌린다. "
                    + "amount는 최초 계산 시점 값(quantity × unitPrice)을 그대로 재사용하며 재계산하지 않는다."
    )
    @PostMapping("/final-settlements/{finalSettlementBatchId}/retry")
    public ResponseEntity<ApiResponse<FinalSettlementRetryResponse>> retryFinalSettlement(
            @Parameter(hidden = true) @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @Parameter(description = "재시도할 최종 정산 회차 ID") @PathVariable UUID finalSettlementBatchId,
            @RequestBody(required = false) FinalSettlementRetryRequest request) {
        FinalSettlementRetryResponse response = finalSettlementCommandService.retryFinalSettlement(
                role, finalSettlementBatchId, request != null ? request : new FinalSettlementRetryRequest(null));
        finalSettlementDisbursementService.disburseAsync(response.finalSettlementBatchId());
        return ResponseEntity.ok(ApiResponse.success(response, "실패 건 재처리가 시작되었습니다."));
    }
}