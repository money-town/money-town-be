package com.moneykk.moneytown.asset.controller;

import com.moneykk.moneytown.asset.dto.response.InternalAssetResponse;
import com.moneykk.moneytown.asset.service.AssetCommandService;
import com.moneykk.moneytown.asset.service.AssetQueryService;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 내부 자산 조회 API */
@RestController
@RequestMapping("/api/v1/internal/assets")
@RequiredArgsConstructor
public class InternalAssetController {

    private final AssetQueryService assetQueryService;
    private final AssetCommandService assetCommandService;

    /** 공모 등록 전 자산 조회 */
    @GetMapping("/{assetId}")
    public ApiResponse<InternalAssetResponse> getAsset(
            @PathVariable UUID assetId
    ) {
        // 삭제되지 않은 자산 조회
        InternalAssetResponse response =
                assetQueryService.getInternalAsset(assetId);

        // 조회 결과 반환
        return ApiResponse.success(
                response,
                "자산 조회가 완료되었습니다."
        );
    }

    /** 최종 정산 완료 후 자산 종료 확정 */
    @PatchMapping("/{assetId}/termination-completion")
    public ApiResponse<Void> completeTermination(
            @PathVariable UUID assetId,
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role
    ) {
        assetCommandService.completeAssetTermination(assetId, role);
        return ApiResponse.success(null, "자산 운영 종료가 완료되었습니다.");
    }
}
