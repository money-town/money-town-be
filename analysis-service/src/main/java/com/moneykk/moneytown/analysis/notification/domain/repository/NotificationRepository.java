package com.moneykk.moneytown.analysis.notification.domain.repository;

import com.moneykk.moneytown.analysis.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Optional<Notification> findByIdAndIsDeletedFalse(UUID id);
    Optional<Notification> findByIdempotencyKey(UUID idempotencyKey);
}
