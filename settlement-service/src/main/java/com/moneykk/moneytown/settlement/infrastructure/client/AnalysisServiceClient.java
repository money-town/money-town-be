package com.moneykk.moneytown.settlement.infrastructure.client;

import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.NotificationRequest;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.NotificationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@FeignClient(name = "analysis-service")
public interface AnalysisServiceClient {

    @PostMapping("/api/v1/internal/notifications")
    ApiResponse<NotificationResponse> sendNotification(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @RequestBody NotificationRequest request);
}