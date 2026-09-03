package com.moneykk.moneytown.asset.controller;

import com.moneykk.moneytown.asset.dto.request.RevenueCreateRequest;
import com.moneykk.moneytown.asset.dto.request.RevenueTransferStatusRequest;
import com.moneykk.moneytown.asset.dto.response.RevenueDetailResponse;
import com.moneykk.moneytown.asset.dto.response.RevenueListResponse;
import com.moneykk.moneytown.asset.dto.response.RevenueTransferStatusResponse;
import com.moneykk.moneytown.asset.service.RevenueCommandService;
import com.moneykk.moneytown.asset.service.RevenueQueryService;
import com.moneykk.moneytown.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 수익 조회 및 상태 변경 API
 */
@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
public class RevenueController {

    private final RevenueQueryService revenueQueryService;
    private final RevenueCommandService revenueCommandService;

    /**
     * 정산 회차 개시 시 수익 금액 조회
     */
    @GetMapping("/{assetId}/revenues/{revenueId}")
    public ApiResponse<RevenueDetailResponse> getRevenue(
            @PathVariable UUID assetId,
            @PathVariable UUID revenueId
    ) {
        RevenueDetailResponse response =
                revenueQueryService.getRevenue(assetId, revenueId);

        return ApiResponse.success(
                response,
                "수익 조회가 완료되었습니다."
        );
    }

    /**
     * 정산 서비스 전달 결과 반영
     */
    @PatchMapping("/revenues/{revenueId}/transfer-status")
    public ApiResponse<RevenueTransferStatusResponse> updateTransferStatus(
            @PathVariable UUID revenueId,
            @Valid @RequestBody RevenueTransferStatusRequest request
    ) {
        RevenueTransferStatusResponse response =
                revenueCommandService.updateTransferStatus(revenueId, request);

        return ApiResponse.success(
                response,
                "수익 전달 상태가 변경되었습니다."
        );
    }

    /**
     * 자산 수익 등록
     */
    @PostMapping("/{assetId}/revenues")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RevenueDetailResponse> createRevenue(
            @PathVariable UUID assetId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody RevenueCreateRequest request
    ) {
        // 권한·중복 확인 후 수익 등록
        RevenueDetailResponse response =
                revenueCommandService.createRevenue(
                        assetId,
                        userId,
                        role,
                        request
                );

        return ApiResponse.success(
                response,
                "수익이 등록되었습니다."
        );
    }

    /**
     * 자산별 수익 목록 조회
     */
    @GetMapping("/{assetId}/revenues")
    public ApiResponse<RevenueListResponse> getRevenues(
            @PathVariable UUID assetId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,

            // 첫 요청에는 커서 생략
            @RequestParam(required = false) UUID cursor,


            // 기본 20건, 최대 100건 조회
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "조회 크기는 1 이상이어야 합니다.")
            @Max(value = 100, message = "조회 크기는 100 이하여야 합니다.")
            int size,

            // 기본 정렬은 등록일 내림차순
            @RequestParam(defaultValue = "DESC") Sort.Direction direction
    ) {
        // 권한과 소유자를 확인한 뒤 목록 조회
        RevenueListResponse response = revenueQueryService.getRevenues(
                assetId,
                userId,
                role,
                cursor,
                size,
                direction
        );

        return ApiResponse.success(
                response,
                "자산별 수익 목록 조회가 완료되었습니다."
        );
    }
}