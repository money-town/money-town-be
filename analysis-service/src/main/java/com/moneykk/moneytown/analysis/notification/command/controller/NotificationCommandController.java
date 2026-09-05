package com.moneykk.moneytown.analysis.notification.command.controller;

import com.moneykk.moneytown.analysis.global.exception.AnalysisErrorCode;
import com.moneykk.moneytown.analysis.notification.command.application.NotificationCommandService;
import com.moneykk.moneytown.analysis.notification.command.dto.request.NotificationTestRequest;
import com.moneykk.moneytown.analysis.notification.command.dto.response.NotificationResponse;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/analysis/notifications")
public class NotificationCommandController {

    private final NotificationCommandService notificationCommandService;

    @PostMapping("/test")
    public ApiResponse<NotificationResponse> sendTest(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody NotificationTestRequest request
            ){
        if(!"ADMIN".equals(role)){
            throw new BusinessException(AnalysisErrorCode.NOTIFICATION_FORBIDDEN);
        }
        return ApiResponse.success(notificationCommandService.sendTest(idempotencyKey ,request), "Slack 테스트 알림 전송에 성공했습니다.");
    }
}
