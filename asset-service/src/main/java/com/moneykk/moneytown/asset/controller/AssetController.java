package com.moneykk.moneytown.asset.controller;

import com.moneykk.moneytown.asset.dto.request.AssetCreateRequest;
import com.moneykk.moneytown.asset.dto.response.AssetCreateResponse;
import com.moneykk.moneytown.asset.dto.response.AssetDetailResponse;
import com.moneykk.moneytown.asset.dto.response.AssetListResponse;
import com.moneykk.moneytown.asset.service.AssetCommandService;
import com.moneykk.moneytown.asset.service.AssetQueryService;
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
 * 자산 관리 API
 */
@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetCommandService assetCommandService;
    private final AssetQueryService assetQueryService;

    /**
     * 자산 등록
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AssetCreateResponse> createAsset(
            @RequestHeader("X-User-Id") java.util.UUID userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody AssetCreateRequest request
    ) {
        // 권한 확인 후 자산 저장
        AssetCreateResponse response =
                assetCommandService.createAsset(userId, role, request);

        return ApiResponse.success(
                response,
                "자산이 등록되었습니다."
        );
    }

    /**
     * 자산 목록 조회
     */
    @GetMapping
    public ApiResponse<AssetListResponse> getAssets(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,

            // 첫 요청에는 커서 생략
            @RequestParam(required = false) UUID cursor,

            // 기본 20건, 최대 100건
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "조회 크기는 1 이상이어야 합니다.")
            @Max(value = 100, message = "조회 크기는 100 이하여야 합니다.")
            int size,

            // 기본은 최신 등록순
            @RequestParam(defaultValue = "DESC") Sort.Direction direction
    ) {
        // 권한별 조회 범위와 정렬 적용
        AssetListResponse response = assetQueryService.getAssets(
                userId,
                role,
                cursor,
                size,
                direction
        );

        return ApiResponse.success(
                response,
                "자산 목록 조회가 완료되었습니다."
        );
    }

    /**
     * 자산 상세 조회
     */
    @GetMapping("/{assetId}")
    public ApiResponse<AssetDetailResponse> getAsset(
            @PathVariable UUID assetId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role
    ) {
        // 조회 권한 확인 후 자산 상세 반환
        AssetDetailResponse response = assetQueryService.getAsset(
                assetId,
                userId,
                role
        );

        return ApiResponse.success(
                response,
                "자산 상세 조회가 완료되었습니다."
        );
    }
}