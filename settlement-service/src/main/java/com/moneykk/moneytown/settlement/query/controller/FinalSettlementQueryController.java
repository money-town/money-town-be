package com.moneykk.moneytown.settlement.query.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.query.application.FinalSettlementQueryService;
import com.moneykk.moneytown.settlement.query.dto.FinalSettlementBatchDetailResponse;
import com.moneykk.moneytown.settlement.query.dto.FinalSettlementPayoutListItemResponse;
import com.moneykk.moneytown.settlement.query.dto.FinalSettlementReconciliationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Final Settlement", description = "최종 정산(원금반환) 개시·재시도 커맨드 API")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FinalSettlementQueryController {

    private final FinalSettlementQueryService finalSettlementQueryService;

    @Operation(
            summary = "최종 정산 회차 상태 조회",
            description = "ADMIN 권한으로 최종 정산(원금반환) 회차 상태와 반환 건수 집계(전체/완료/실패/대기중)를 조회한다."
    )
    @GetMapping("/final-settlements/{finalSettlementBatchId}")
    public ResponseEntity<ApiResponse<FinalSettlementBatchDetailResponse>> getFinalSettlementBatch(
            @Parameter(hidden = true) @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @Parameter(description = "조회할 최종 정산 회차 ID") @PathVariable UUID finalSettlementBatchId) {
        FinalSettlementBatchDetailResponse response = finalSettlementQueryService.getFinalSettlementBatch(role, finalSettlementBatchId);
        return ResponseEntity.ok(ApiResponse.success(response, "최종 정산 회차 상태를 조회했습니다."));
    }

    @Operation(
            summary = "회차별 개별 반환 내역 조회",
            description = "ADMIN 권한으로 최종 정산 회차의 투자자별 원금반환 내역을 페이지 조회한다. status를 생략하면 전체 상태를 조회하며, "
                    + "DEAD_LETTER로 필터링하면 retryCount 내림차순, 그 외에는 amount 내림차순으로 정렬된다."
    )
    @GetMapping("/final-settlements/{finalSettlementBatchId}/payouts")
    public ResponseEntity<ApiResponse<PageResponse<FinalSettlementPayoutListItemResponse>>> getPayouts(
            @Parameter(hidden = true) @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @Parameter(description = "조회할 최종 정산 회차 ID") @PathVariable UUID finalSettlementBatchId,
            @Parameter(description = "반환 상태 필터 (생략 시 전체 조회)") @RequestParam(required = false) PayoutStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<FinalSettlementPayoutListItemResponse> response =
                finalSettlementQueryService.getPayouts(role, finalSettlementBatchId, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(response, "개별 반환 내역을 조회했습니다."));
    }

    @Operation(
            summary = "최종 정산 회차 정합성 검증",
            description = "ADMIN 권한으로 원금반환 총액(totalAmount)과 반환 내역 전체 합계를 대사한다. "
                    + "두 값이 다르면(reconciled=false) 반환 건 누락·중복 등 데이터 정합성 문제를 의심할 수 있다. "
                    + "paidAmount는 PAID 상태 건만 합산한 참고값이다."
    )
    @GetMapping("/final-settlements/{finalSettlementBatchId}/reconciliation")
    public ResponseEntity<ApiResponse<FinalSettlementReconciliationResponse>> getReconciliation(
            @Parameter(hidden = true) @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @Parameter(description = "정합성을 검증할 최종 정산 회차 ID") @PathVariable UUID finalSettlementBatchId) {
        FinalSettlementReconciliationResponse response =
                finalSettlementQueryService.getReconciliation(role, finalSettlementBatchId);
        return ResponseEntity.ok(ApiResponse.success(response, "원금반환 총액과 지급 내역의 정합성을 확인했습니다."));
    }
}