package com.moneykk.moneytown.analysis.notification.command.controller;

import com.moneykk.moneytown.analysis.notification.command.application.NotificationCommandService;
import com.moneykk.moneytown.analysis.notification.command.dto.request.NotificationRequest;
import com.moneykk.moneytown.analysis.notification.command.dto.response.NotificationResponse;
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
@RequestMapping("/api/v1/internal/notifications")
public class NotificationInternalController {

    private final NotificationCommandService notificationCommandService;

    @PostMapping
    public ApiResponse<NotificationResponse> send(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody NotificationRequest request
            ){
        return ApiResponse.success(
                notificationCommandService.send(idempotencyKey, request),
                "알림을 접수했습니다."
        );
    }
}
