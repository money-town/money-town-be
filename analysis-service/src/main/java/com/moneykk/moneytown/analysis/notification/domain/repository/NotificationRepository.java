package com.moneykk.moneytown.analysis.notification.domain.repository;

import com.moneykk.moneytown.analysis.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Optional<Notification> findByIdAndIsDeletedFalse(UUID id);
    Page<Notification> findAllByIsDeletedFalse(Pageable pageable);
}
