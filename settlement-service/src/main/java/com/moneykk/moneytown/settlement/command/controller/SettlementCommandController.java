package com.moneykk.moneytown.settlement.command.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.settlement.command.application.DividendDisbursementService;
import com.moneykk.moneytown.settlement.command.application.SettlementCommandService;
import com.moneykk.moneytown.settlement.command.dto.OpenSettlementRequest;
import com.moneykk.moneytown.settlement.command.dto.SettlementBatchResponse;
import com.moneykk.moneytown.settlement.infrastructure.client.RevenueTransferStatusNotifier;
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

@Tag(name = "Settlement", description = "정산 회차(배당) 개시·재시도 커맨드 API")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SettlementCommandController {

    private final SettlementCommandService settlementCommandService;
    private final DividendDisbursementService dividendDisbursementService;
    private final RevenueTransferStatusNotifier revenueTransferStatusNotifier;

    @Operation(
            summary = "정산 회차 개시",
            description = "ADMIN 권한으로 특정 수익 건에 대한 정산 회차를 개시한다. 수익 검증(READY 상태, 금액 유효성) → 지분 스냅샷 조회·저장 "
                    + "→ 지분율 기반 배당금 계산 → DividendPayout을 QUEUED 상태로 저장 → asset-service에 수익 전달 완료(TRANSFERRED) 통보 "
                    + "→ 비동기 지급 시작 순서로 처리된다. recordDate(배당 기준일)를 생략하면 수익의 발생 기간 종료일(periodEnd)로 대체한다."
    )
    @PostMapping("/settlements")
    public ResponseEntity<ApiResponse<SettlementBatchResponse>> openSettlementBatch(
            @Parameter(hidden = true) @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @Valid @RequestBody OpenSettlementRequest request) {
        SettlementBatchResponse response =
                settlementCommandService.openBatch(role, request.assetId(), request.revenueId(), request.recordDate());
        revenueTransferStatusNotifier.notifyTransferred(response.revenueId());
        dividendDisbursementService.disburseAsync(response.settlementBatchId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "정산 회차가 개시되었습니다."));
    }

    @Operation(
            summary = "정산 회차 재시도",
            description = "ADMIN 권한으로 FAILED/PARTIAL_FAILED 상태인 정산 회차의 DEAD_LETTER 지급 건을 QUEUED로 되돌리고, "
                    + "회차 상태를 DISBURSING으로 전환한 뒤 비동기 지급을 재시작한다."
    )
    @PostMapping("/settlements/{settlementBatchId}/retry")
    public ResponseEntity<ApiResponse<SettlementBatchResponse>> retrySettlementBatch(
            @Parameter(hidden = true) @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @Parameter(description = "재시도할 정산 회차 ID") @PathVariable UUID settlementBatchId) {
        SettlementBatchResponse response = settlementCommandService.retryBatch(role, settlementBatchId);
        dividendDisbursementService.disburseAsync(response.settlementBatchId());
        return ResponseEntity.ok(ApiResponse.success(response, "정산 회차 재시도가 접수되었습니다."));
    }
}