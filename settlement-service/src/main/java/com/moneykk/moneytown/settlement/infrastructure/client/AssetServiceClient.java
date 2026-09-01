package com.moneykk.moneytown.settlement.infrastructure.client;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.HoldingsSnapshotResponse;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.RevenueResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.UUID;

@FeignClient(name = "asset-service")
public interface AssetServiceClient {

    @GetMapping("/api/v1/assets/{assetId}/revenues/{revenueId}")
    ApiResponse<RevenueResponse> getRevenue(@PathVariable("assetId") UUID assetId,
                                             @PathVariable("revenueId") UUID revenueId);

    @GetMapping("/api/v1/assets/{assetId}/holdings")
    ApiResponse<HoldingsSnapshotResponse> getHoldingsSnapshot(@PathVariable("assetId") UUID assetId,
                                                               @RequestParam("asOf") LocalDate asOf,
                                                               @RequestParam(value = "cursor", required = false) String cursor);
}