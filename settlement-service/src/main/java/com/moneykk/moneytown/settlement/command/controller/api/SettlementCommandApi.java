package com.moneykk.moneytown.settlement.command.controller.api;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.settlement.command.dto.OpenSettlementRequest;
import com.moneykk.moneytown.settlement.command.dto.SettlementBatchResponse;
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

@Tag(name = "Settlement", description = "정산 회차(배당) 개시·재시도 커맨드 API")
@RequestMapping("/api/v1")
public interface SettlementCommandApi {

    @Operation(
            summary = "정산 회차 개시",
            description = "ADMIN 권한으로 특정 수익 건에 대한 정산 회차를 개시한다. 수익 검증(READY 상태, 금액 유효성) → 지분 스냅샷 조회·저장 "
                    + "→ 지분율 기반 배당금 계산 → DividendPayout을 QUEUED 상태로 저장 → asset-service에 수익 전달 완료(TRANSFERRED) 통보 "
                    + "→ 비동기 지급 시작 순서로 처리된다. recordDate(배당 기준일)를 생략하면 수익의 발생 기간 종료일(periodEnd)로 대체한다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "정산 회차 개시 성공",
                    content = @Content(schema = @Schema(implementation = SettlementBatchResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "수익 데이터의 자산 불일치(SETTLEMENT_400_01) 또는 수익 금액 무효(SETTLEMENT_400_03)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "ADMIN 권한이 아님 (SETTLEMENT_403_01)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "수익 데이터 없음(SETTLEMENT_404_01) 또는 보유지분 스냅샷 조회 실패(SETTLEMENT_404_02)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "READY 상태 아님(409_01) / 배당가능총액 0 이하(409_02) / 이미 개시된 회차 존재(409_03) "
                            + "/ 자산에 진행 중인 회차 존재(409_04) / 보유지분 스냅샷 무효(409_05)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "자산 서비스 보유지분 스냅샷 페이지네이션 정체 (SETTLEMENT_500_01)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/settlements")
    ResponseEntity<ApiResponse<SettlementBatchResponse>> openSettlementBatch(
            @Parameter(hidden = true) @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @Valid @RequestBody OpenSettlementRequest request);

    @Operation(
            summary = "정산 회차 재시도",
            description = "ADMIN 권한으로 FAILED/PARTIAL_FAILED 상태인 정산 회차의 DEAD_LETTER 지급 건을 QUEUED로 되돌리고, "
                    + "회차 상태를 DISBURSING으로 전환한 뒤 비동기 지급을 재시작한다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "정산 회차 재시도 접수 성공",
                    content = @Content(schema = @Schema(implementation = SettlementBatchResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "ADMIN 권한이 아님 (SETTLEMENT_403_01)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "정산 회차를 찾을 수 없음 (SETTLEMENT_404_03)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "FAILED/PARTIAL_FAILED 상태가 아니어서 재시도 불가 (SETTLEMENT_409_06)",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/settlements/{settlementBatchId}/retry")
    ResponseEntity<ApiResponse<SettlementBatchResponse>> retrySettlementBatch(
            @Parameter(hidden = true) @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @Parameter(description = "재시도할 정산 회차 ID") @PathVariable UUID settlementBatchId);
}