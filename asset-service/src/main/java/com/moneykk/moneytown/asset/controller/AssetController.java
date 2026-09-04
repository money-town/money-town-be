package com.moneykk.moneytown.asset.controller;

import com.moneykk.moneytown.asset.dto.request.AssetCreateRequest;
import com.moneykk.moneytown.asset.dto.request.AssetStatusUpdateRequest;
import com.moneykk.moneytown.asset.dto.request.AssetUpdateRequest;
import com.moneykk.moneytown.asset.dto.response.AssetCreateResponse;
import com.moneykk.moneytown.asset.dto.response.AssetDetailResponse;
import com.moneykk.moneytown.asset.dto.response.AssetListResponse;
import com.moneykk.moneytown.asset.service.AssetCommandService;
import com.moneykk.moneytown.asset.service.AssetQueryService;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
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
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
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
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,

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
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role
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

    /**
     * 자산 정보 수정
     */
    @PatchMapping("/{assetId}")
    public ApiResponse<Void> updateAsset(
            @PathVariable UUID assetId,
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @Valid @RequestBody AssetUpdateRequest request
    ) {
        // 권한 확인 후 자산 정보 수정
        assetCommandService.updateAsset(
                assetId,
                userId,
                role,
                request
        );

        // 수정 성공 응답
        return ApiResponse.success(
                null,
                "자산 정보가 수정되었습니다."
        );
    }

    /**
     * 자산 상태 변경
     */
    @PatchMapping("/{assetId}/status")
    public ApiResponse<Void> changeAssetStatus(
            @PathVariable UUID assetId,
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @Valid @RequestBody AssetStatusUpdateRequest request
    ) {
        // 권한과 상태 전이를 확인한 후 변경
        assetCommandService.changeAssetStatus(
                assetId,
                userId,
                role,
                request.status(),
                request.rejectionReason()
        );

        return ApiResponse.success(
                null,
                "자산 상태가 변경되었습니다."
        );
    }

    /**
     * 자산 삭제
     */
    @DeleteMapping("/{assetId}")
    public ApiResponse<Void> deleteAsset(
            @PathVariable UUID assetId,
            @RequestHeader(AuthHeaderConstants.USER_ID) UUID userId,
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role
    ) {
        // 권한과 상태를 확인한 후 소프트 삭제
        assetCommandService.deleteAsset(
                assetId,
                userId,
                role
        );

        return ApiResponse.success(
                null,
                "자산이 삭제되었습니다."
        );
    }
}
