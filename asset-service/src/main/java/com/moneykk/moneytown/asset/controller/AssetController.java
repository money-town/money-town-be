package com.moneykk.moneytown.asset.controller;

import com.moneykk.moneytown.asset.dto.request.AssetCreateRequest;
import com.moneykk.moneytown.asset.dto.response.AssetCreateResponse;
import com.moneykk.moneytown.asset.service.AssetCommandService;
import com.moneykk.moneytown.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 자산 관리 API */
@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetCommandService assetCommandService;

    /** 자산 등록 */
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
}