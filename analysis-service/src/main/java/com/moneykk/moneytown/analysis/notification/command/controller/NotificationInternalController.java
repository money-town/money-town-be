package com.moneykk.moneytown.analysis.notification.command.controller;

import com.moneykk.moneytown.analysis.notification.command.application.NotificationCommandService;
import com.moneykk.moneytown.analysis.notification.command.dto.request.NotificationRequest;
import com.moneykk.moneytown.analysis.notification.command.dto.response.NotificationResponse;
import com.moneykk.moneytown.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "알림 (내부)", description = "서비스 간 내부 알림 발송 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/notifications")
public class NotificationInternalController {

    private final NotificationCommandService notificationCommandService;

    @Operation(
            summary = "알림 발송 접수 (내부)",
            description = "다른 서비스가 알림 발송을 요청합니다. userId가 null이면 운영 채널로 발송됩니다."
    )
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
