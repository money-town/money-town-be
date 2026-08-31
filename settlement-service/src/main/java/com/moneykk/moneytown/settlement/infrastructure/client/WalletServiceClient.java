package com.moneykk.moneytown.settlement.infrastructure.client;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.DividendDepositRequest;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.DividendDepositResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "wallet-service")
public interface WalletServiceClient {

    @PostMapping("/api/v1/internal/dividends")
    ApiResponse<DividendDepositResponse> depositDividend(@RequestBody DividendDepositRequest request);
}