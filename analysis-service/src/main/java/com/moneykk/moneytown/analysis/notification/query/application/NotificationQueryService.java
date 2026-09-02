package com.moneykk.moneytown.analysis.notification.query.application;

import com.moneykk.moneytown.analysis.global.exception.AnalysisErrorCode;
import com.moneykk.moneytown.analysis.notification.domain.repository.NotificationRepository;
import com.moneykk.moneytown.analysis.notification.query.dto.NotificationDetailResponse;
import com.moneykk.moneytown.analysis.notification.query.dto.NotificationListItemResponse;
import com.moneykk.moneytown.analysis.notification.query.dto.NotificationSearchCondition;
import com.moneykk.moneytown.analysis.notification.query.repository.NotificationQueryRepository;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueryService {

    private final NotificationQueryRepository queryRepository;
    private final NotificationRepository repository;

    public PageResponse<NotificationListItemResponse> search(NotificationSearchCondition condition, Pageable pageable){
        return PageResponse.from(queryRepository.search(condition,pageable), NotificationListItemResponse::from);
    }

    public NotificationDetailResponse getById(UUID notificationId){
        return repository.findByIdAndIsDeletedFalse(notificationId)
                .map(NotificationDetailResponse::from)
                .orElseThrow(() -> new BusinessException(AnalysisErrorCode.NOTIFICATION_NOT_FOUND));
    }
}
