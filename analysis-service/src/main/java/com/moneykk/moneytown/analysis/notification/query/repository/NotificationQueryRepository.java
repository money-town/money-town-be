package com.moneykk.moneytown.analysis.notification.query.repository;

import com.moneykk.moneytown.analysis.notification.domain.Notification;
import com.moneykk.moneytown.analysis.notification.query.dto.NotificationSearchCondition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationQueryRepository {
    Page<Notification> search(NotificationSearchCondition c, Pageable pageable);
}
