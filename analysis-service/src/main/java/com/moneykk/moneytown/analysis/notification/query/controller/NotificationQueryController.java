package com.moneykk.moneytown.analysis.notification.query.controller;

import com.moneykk.moneytown.analysis.global.exception.AnalysisErrorCode;
import com.moneykk.moneytown.analysis.notification.query.application.NotificationQueryService;
import com.moneykk.moneytown.analysis.notification.query.dto.NotificationDetailResponse;
import com.moneykk.moneytown.analysis.notification.query.dto.NotificationListItemResponse;
import com.moneykk.moneytown.analysis.notification.query.dto.NotificationSearchCondition;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
public class NotificationQueryController {

    private final NotificationQueryService queryService;

    @GetMapping
    public ApiResponse<PageResponse<NotificationListItemResponse>> getNotifications(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            NotificationSearchCondition condition,
            Pageable pageable
    ){
        if(!"ADMIN".equals(role)){
            throw new BusinessException(AnalysisErrorCode.NOTIFICATION_FORBIDDEN);
        }

        return ApiResponse.success(queryService.search(condition, pageable), "알림 목록을 조회했습니다.");
    }

    @GetMapping("/{notificationId}")
    public ApiResponse<NotificationDetailResponse> getNotification(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable UUID notificationId
    ){
        if(!"ADMIN".equals(role)){
            throw new BusinessException(AnalysisErrorCode.NOTIFICATION_FORBIDDEN);
        }
        return ApiResponse.success(queryService.getById(notificationId), "알림을 조회했습니다.");
    }
}
