package com.moneykk.moneytown.settlement.query.controller.api;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.settlement.domain.entity.PayoutStatus;
import com.moneykk.moneytown.settlement.query.dto.DividendPayoutListItemResponse;
import com.moneykk.moneytown.settlement.query.dto.MyDividendPayoutListItemResponse;
import com.moneykk.moneytown.settlement.query.dto.SettlementBatchDetailResponse;
import com.moneykk.moneytown.settlement.query.dto.SettlementReconciliationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@Tag(name = "Settlement", description = "정산 회차(배당) 조회 및 개시·재시도 커맨드 API")
@RequestMapping("/api/v1")
public interface SettlementQueryApi {

    @Operation(
            summary = "정산 회차 상태 조회",
            description = "ADMIN 권한으로 정산 회차 상태와 지급 건수 집계(전체/완료/실패/대기중)를 조회한다. Soft Delete된 회차는 조회되지 않는다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = SettlementBatchDetailResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "ADMIN 권한이 아님 (SETTLEMENT_403_01)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "정산 회차를 찾을 수 없음 (SETTLEMENT_404_03)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping("/settlements/{settlementBatchId}")
    ResponseEntity<ApiResponse<SettlementBatchDetailResponse>> getSettlementBatch(
            @Parameter(hidden = true) @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @Parameter(description = "조회할 정산 회차 ID") @PathVariable UUID settlementBatchId);

    @Operation(
            summary = "회차별 개별 지급 내역 조회",
            description = "ADMIN 권한으로 정산 회차의 투자자별 배당 지급 내역을 페이지 조회한다. status를 생략하면 전체 상태를 조회하며, "
                    + "DEAD_LETTER로 필터링하면 retryCount 내림차순, 그 외에는 amount 내림차순으로 정렬된다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "ADMIN 권한이 아님 (SETTLEMENT_403_01)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "정산 회차를 찾을 수 없음 (SETTLEMENT_404_03)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping("/settlements/{settlementBatchId}/payouts")
    ResponseEntity<ApiResponse<PageResponse<DividendPayoutListItemResponse>>> getPayouts(
            @Parameter(hidden = true) @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @Parameter(description = "조회할 정산 회차 ID") @PathVariable UUID settlementBatchId,
            @Parameter(description = "지급 상태 필터 (생략 시 전체 조회)") @RequestParam(required = false) PayoutStatus status,
            @PageableDefault(size = 10) Pageable pageable);

    @Operation(
            summary = "정산 회차 정합성 검증",
            description = "ADMIN 권한으로 배당 총액(totalAmount - remainderAmount)과 지급 내역 전체 합계를 대사한다. "
                    + "두 값이 다르면(reconciled=false) 지급 건 누락·중복 등 데이터 정합성 문제를 의심할 수 있다. "
                    + "paidAmount는 PAID 상태 건만 합산한 참고값으로, 회차가 아직 완전히 지급되지 않은 정상적인 진행 상태와는 구분된다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공 (reconciled 값과 무관하게 200으로 응답)",
                    content = @Content(schema = @Schema(implementation = SettlementReconciliationResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "ADMIN 권한이 아님 (SETTLEMENT_403_01)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "정산 회차를 찾을 수 없음 (SETTLEMENT_404_03)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping("/settlements/{settlementBatchId}/reconciliation")
    ResponseEntity<ApiResponse<SettlementReconciliationResponse>> getReconciliation(
            @Parameter(hidden = true) @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @Parameter(description = "정합성을 검증할 정산 회차 ID") @PathVariable UUID settlementBatchId);

    // TODO: Gateway 인증/인가 정책 확정 후 INVESTOR 권한 및 사용자 정보 전달 방식 재검토
    @Operation(
            tags = "Settlement",
            summary = "내 배당 내역·산출 근거 조회",
            description = "로그인한 투자자 본인의 배당 지급 내역을 페이지 조회한다. assetId를 지정하면 해당 자산으로 필터링하고, "
                    + "생략하면 투자자의 전체 배당 내역을 조회한다. paidAt은 PAID 상태일 때만 값이 채워진다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = PageResponse.class)))
    })
    @GetMapping("/dividends/me")
    ResponseEntity<ApiResponse<PageResponse<MyDividendPayoutListItemResponse>>> getMyDividends(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID investorId,
            @Parameter(description = "자산 ID 필터 (생략 시 전체 자산 조회)") @RequestParam(required = false) UUID assetId,
            @PageableDefault(size = 20) Pageable pageable);
}