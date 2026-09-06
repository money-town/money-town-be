package com.moneykk.moneytown.settlement.query.controller;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.query.application.SettlementQueryService;
import com.moneykk.moneytown.settlement.query.dto.DividendPayoutListItemResponse;
import com.moneykk.moneytown.settlement.query.dto.MyDividendPayoutListItemResponse;
import com.moneykk.moneytown.settlement.query.dto.SettlementBatchDetailResponse;
import com.moneykk.moneytown.settlement.query.dto.SettlementReconciliationResponse;
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

@Tag(name = "Settlement", description = "정산 회차(배당) 개시·재시도 커맨드 API")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SettlementQueryController {

    private final SettlementQueryService settlementQueryService;

    @Operation(
            summary = "정산 회차 상태 조회",
            description = "ADMIN 권한으로 정산 회차 상태와 지급 건수 집계(전체/완료/실패/대기중)를 조회한다. Soft Delete된 회차는 조회되지 않는다."
    )
    @GetMapping("/settlements/{settlementBatchId}")
    public ResponseEntity<ApiResponse<SettlementBatchDetailResponse>> getSettlementBatch(
            @Parameter(hidden = true) @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @Parameter(description = "조회할 정산 회차 ID") @PathVariable UUID settlementBatchId) {
        SettlementBatchDetailResponse response = settlementQueryService.getSettlementBatch(role, settlementBatchId);
        return ResponseEntity.ok(ApiResponse.success(response, "정산 회차 상태를 조회했습니다."));
    }

    @Operation(
            summary = "회차별 개별 지급 내역 조회",
            description = "ADMIN 권한으로 정산 회차의 투자자별 배당 지급 내역을 페이지 조회한다. status를 생략하면 전체 상태를 조회하며, "
                    + "DEAD_LETTER로 필터링하면 retryCount 내림차순, 그 외에는 amount 내림차순으로 정렬된다."
    )
    @GetMapping("/settlements/{settlementBatchId}/payouts")
    public ResponseEntity<ApiResponse<PageResponse<DividendPayoutListItemResponse>>> getPayouts(
            @Parameter(hidden = true) @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @Parameter(description = "조회할 정산 회차 ID") @PathVariable UUID settlementBatchId,
            @Parameter(description = "지급 상태 필터 (생략 시 전체 조회)") @RequestParam(required = false) PayoutStatus status,
            @PageableDefault(size = 10) Pageable pageable) {
        PageResponse<DividendPayoutListItemResponse> response = settlementQueryService.getPayouts(role, settlementBatchId, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(response, "회차별 개별 지급 내역을 조회했습니다."));
    }

    @Operation(
            summary = "정산 회차 정합성 검증",
            description = "ADMIN 권한으로 배당 총액(totalAmount - remainderAmount)과 지급 내역 전체 합계를 대사한다. "
                    + "두 값이 다르면(reconciled=false) 지급 건 누락·중복 등 데이터 정합성 문제를 의심할 수 있다. "
                    + "paidAmount는 PAID 상태 건만 합산한 참고값으로, 회차가 아직 완전히 지급되지 않은 정상적인 진행 상태와는 구분된다."
    )
    @GetMapping("/settlements/{settlementBatchId}/reconciliation")
    public ResponseEntity<ApiResponse<SettlementReconciliationResponse>> getReconciliation(
            @Parameter(hidden = true) @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @Parameter(description = "정합성을 검증할 정산 회차 ID") @PathVariable UUID settlementBatchId) {
        SettlementReconciliationResponse response = settlementQueryService.getReconciliation(role, settlementBatchId);
        return ResponseEntity.ok(ApiResponse.success(response, "배당 총액과 지급 내역의 정합성을 확인했습니다."));
    }

    // TODO: Gateway 인증/인가 정책 확정 후 INVESTOR 권한 및 사용자 정보 전달 방식 재검토
    @Operation(
            tags = "Settlement",
            summary = "내 배당 내역·산출 근거 조회",
            description = "로그인한 투자자 본인의 배당 지급 내역을 페이지 조회한다. assetId를 지정하면 해당 자산으로 필터링하고, "
                    + "생략하면 투자자의 전체 배당 내역을 조회한다. paidAt은 PAID 상태일 때만 값이 채워진다."
    )
    @GetMapping("/dividends/me")
    public ResponseEntity<ApiResponse<PageResponse<MyDividendPayoutListItemResponse>>> getMyDividends(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID investorId,
            @Parameter(description = "자산 ID 필터 (생략 시 전체 자산 조회)") @RequestParam(required = false) UUID assetId,
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<MyDividendPayoutListItemResponse> response = settlementQueryService.getMyDividends(investorId, assetId, pageable);
        return ResponseEntity.ok(ApiResponse.success(response, "배당 내역을 조회했습니다."));
    }
}