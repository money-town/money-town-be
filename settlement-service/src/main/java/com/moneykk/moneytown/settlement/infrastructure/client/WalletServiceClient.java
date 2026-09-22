package com.moneykk.moneytown.settlement.infrastructure.client;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.SettlementDepositRequest;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.SettlementDepositResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

// 배당 지급(depositDividend)은 Kafka
// 최종 정산(depositSettlement)만 동기 Feign
@FeignClient(name = "wallet-service")
public interface WalletServiceClient {

    @PostMapping("/api/v1/internal/settlements")
    ApiResponse<SettlementDepositResponse> depositSettlement(@RequestBody SettlementDepositRequest request);
}