package com.moneykk.moneytown.offering.offering.infrastructure.client;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.offering.offering.infrastructure.client.dto.AssetOfferingInfoResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@FeignClient(name = "asset-service")
public interface AssetServiceClient {

    @GetMapping("/api/v1/internal/assets/{assetId}")
    ApiResponse<AssetOfferingInfoResponse> getAsset(
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @PathVariable("assetId") UUID assetId
    );
}