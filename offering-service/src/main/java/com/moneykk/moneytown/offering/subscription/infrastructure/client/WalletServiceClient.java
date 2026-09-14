package com.moneykk.moneytown.offering.subscription.infrastructure.client;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.WalletHoldStatusResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(
        name = "wallet-service",
        contextId = "walletHoldServiceClient"
)
public interface WalletServiceClient {

    @GetMapping("/api/v1/internal/wallet-holds/{subscriptionId}")
    ApiResponse<WalletHoldStatusResponse> getWalletHoldStatus(
            @PathVariable("subscriptionId") UUID subscriptionId
    );
}