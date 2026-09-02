package com.moneykk.moneytown.offering.subscription.infrastructure.client;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.PreFdsCheckRequest;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.PreFdsCheckResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "analysis-service")
public interface AnalysisServiceClient {

    @PostMapping("/api/v1/internal/fds/check")
    ApiResponse<PreFdsCheckResponse> check(
            @RequestBody PreFdsCheckRequest request
    );
}