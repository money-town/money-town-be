package com.moneykk.moneytown.analysis.ai.infrastructure.client;

import com.moneykk.moneytown.analysis.ai.infrastructure.client.dto.AssetSummary;
import com.moneykk.moneytown.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "asset-service")
public interface AssetServiceClient {

    @GetMapping("/api/v1/internal/assets")
    ApiResponse<List<AssetSummary>> getAssets(
            @RequestParam List<UUID> assetIds
            );
}
