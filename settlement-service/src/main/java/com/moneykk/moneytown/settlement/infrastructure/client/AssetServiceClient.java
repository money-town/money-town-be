package com.moneykk.moneytown.settlement.infrastructure.client;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.HoldingsSnapshotResponse;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.ReadyRevenueListResponse;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.RevenueResponse;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.RevenueTransferStatusUpdateRequest;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.RevenueTransferStatusUpdateResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "asset-service")
public interface AssetServiceClient {

    @GetMapping("/api/v1/assets/{assetId}/revenues/{revenueId}")
    ApiResponse<RevenueResponse> getRevenue(@PathVariable("assetId") UUID assetId,
                                            @PathVariable("revenueId") UUID revenueId);

    @GetMapping("/api/v1/assets/{assetId}/holdings")
    ApiResponse<HoldingsSnapshotResponse> getHoldingsSnapshot(@RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
                                                              @PathVariable("assetId") UUID assetId,
                                                              @RequestParam("asOf") String asOf,
                                                              @RequestParam(value = "cursor", required = false) UUID cursor);

    @PatchMapping("/api/v1/assets/revenues/{revenueId}/transfer-status")
    ApiResponse<RevenueTransferStatusUpdateResponse> updateRevenueTransferStatus(
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @PathVariable("revenueId") UUID revenueId,
            @RequestBody RevenueTransferStatusUpdateRequest request);

    @GetMapping("/api/v1/internal/revenues")
    ApiResponse<ReadyRevenueListResponse> getReadyRevenues(
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @RequestParam(value = "cursor", required = false) UUID cursor
    );

    @PatchMapping("/api/v1/internal/assets/{assetId}/termination-completion")
    ApiResponse<Void> completeAssetTermination(
            @PathVariable("assetId") UUID assetId,
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role
    );
}