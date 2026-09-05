package com.moneykk.moneytown.asset.controller;

import com.moneykk.moneytown.asset.dto.response.HoldingSnapshotResponse;
import com.moneykk.moneytown.asset.dto.response.MyAssetHoldingResponse;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.service.HoldingQueryService;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 보유지분 조회 API
 */
@Validated
@RestController
@RequestMapping("/api/v1/assets/{assetId}/holdings")
@RequiredArgsConstructor
public class HoldingController {

    private final HoldingQueryService holdingQueryService;

    /**
     * 배당 기준일의 보유지분 조회
     */
    @GetMapping
    public ApiResponse<HoldingSnapshotResponse> getSnapshot(
            @PathVariable UUID assetId,

            @RequestHeader(AuthHeaderConstants.USER_ROLE)
            String role,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate asOf,

            @RequestParam(required = false) UUID cursor,

            @RequestParam(defaultValue = "100")
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
            int size,

            // 기본 정렬은 등록일 내림차순
            @RequestParam(defaultValue = "DESC") Sort.Direction direction
    ) {
        // 내부 시스템만 스냅샷 조회 가능
        if (!"SYSTEM".equals(role)) {
            throw new BusinessException(
                    AssetErrorCode.HOLDING_SNAPSHOT_ACCESS_DENIED
            );
        }

        // 기준일 보유지분 조회
        HoldingSnapshotResponse response =
                holdingQueryService.getSnapshot(assetId, asOf, cursor, size, direction);

        return ApiResponse.success(
                response,
                "기준일 보유지분 조회가 완료되었습니다."
        );
    }

    /**
     * 특정 자산의 내 보유지분 조회
     */
    @GetMapping("/me")
    public ApiResponse<MyAssetHoldingResponse> getMyHolding(
            @PathVariable UUID assetId,

            @RequestHeader(AuthHeaderConstants.USER_ID)
            UUID userId,

            @RequestHeader(AuthHeaderConstants.USER_ROLE)
            String role
    ) {
        // 로그인한 투자자의 보유지분 조회
        MyAssetHoldingResponse response =
                holdingQueryService.getMyHolding(
                        assetId,
                        userId,
                        role
                );

        return ApiResponse.success(
                response,
                "내 보유지분 조회가 완료되었습니다."
        );
    }
}
