package com.moneykk.moneytown.settlement.command.controller.api;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.settlement.command.dto.FinalSettlementBatchResponse;
import com.moneykk.moneytown.settlement.command.dto.FinalSettlementRetryRequest;
import com.moneykk.moneytown.settlement.command.dto.FinalSettlementRetryResponse;
import com.moneykk.moneytown.settlement.command.dto.OpenFinalSettlementRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@Tag(name = "Final Settlement", description = "최종 정산(원금반환) 조회 및 개시·재시도 커맨드 API")
@RequestMapping("/api/v1")
public interface FinalSettlementCommandApi {

    @Operation(
            tags = "Final Settlement Internal",
            summary = "최종 정산 회차 개시 (자산 서비스 전용 내부 API)",
            description = "자산 서비스가 자산 종료(해지) 확정 시 동기 호출하는 SYSTEM 전용 내부 API다. 종료 시점(terminatedAt) 기준 보유자 스냅샷 조회 "
                    + "→ 보유 수량 × 단가로 총액 계산 → FinalSettlementPayout을 QUEUED 상태로 저장 → 비동기 지급 시작 순서로 처리된다. "
                    + "이미 해당 자산으로 개시된 회차가 있으면 새로 만들지 않고 기존 회차를 그대로 반환한다(멱등)."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "최종 정산 회차 개시 성공 (이미 존재하면 기존 회차를 그대로 반환)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "SYSTEM 권한이 아님 (SETTLEMENT_403_03)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "종료 시점(terminatedAt) 기준 보유자가 존재하지 않음 (SETTLEMENT_409_07)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "자산 서비스 보유지분 스냅샷 페이지네이션 정체 (SETTLEMENT_500_01)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/internal/final-settlements")
    ResponseEntity<ApiResponse<FinalSettlementBatchResponse>> openFinalSettlement(
            @Parameter(hidden = true) @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @Valid @RequestBody OpenFinalSettlementRequest request);

    @Operation(
            summary = "최종 정산 실패 건 재처리",
            description = "ADMIN 권한으로 FAILED/PARTIAL_FAILED 상태인 최종 정산 회차의 실패 건을 재처리한다. "
                    + "finalSettlementPayoutIds를 지정하지 않으면 회차의 DEAD_LETTER 건 전체를, 지정하면 그 건들만 선택적으로 QUEUED로 되돌린다. "
                    + "amount는 최초 계산 시점 값(quantity × unitPrice)을 그대로 재사용하며 재계산하지 않는다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "최종 정산 실패 건 재처리 접수 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "ADMIN 권한이 아님 (SETTLEMENT_403_02)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "최종 정산 회차를 찾을 수 없음 (SETTLEMENT_404_04)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "FAILED/PARTIAL_FAILED 상태가 아니어서 재시도 불가(409_08) 또는 재처리 가능한 실패 건 없음(409_09)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/final-settlements/{finalSettlementBatchId}/retry")
    ResponseEntity<ApiResponse<FinalSettlementRetryResponse>> retryFinalSettlement(
            @Parameter(hidden = true) @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @Parameter(description = "재시도할 최종 정산 회차 ID") @PathVariable UUID finalSettlementBatchId,
            @RequestBody(required = false) FinalSettlementRetryRequest request);
}