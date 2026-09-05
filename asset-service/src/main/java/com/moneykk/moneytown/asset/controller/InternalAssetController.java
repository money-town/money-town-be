package com.moneykk.moneytown.asset.controller;

import com.moneykk.moneytown.asset.dto.response.InternalAssetResponse;
import com.moneykk.moneytown.asset.service.AssetQueryService;
import com.moneykk.moneytown.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 내부 자산 조회 API */
@RestController
@RequestMapping("/api/v1/internal/assets")
@RequiredArgsConstructor
public class InternalAssetController {

    private final AssetQueryService assetQueryService;

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
}