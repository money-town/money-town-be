package com.moneykk.moneytown.analysis.notification.domain.repository;

import com.moneykk.moneytown.analysis.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Optional<Notification> findByIdAndIsDeletedFalse(UUID id);
    Optional<Notification> findByIdempotencyKey(UUID idempotencyKey);


    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE  Notification  n
            SET n.status = com.moneykk.moneytown.analysis.notification.domain.NotificationStatus.FAILED,
                n.errorMessage = :message,
                n.updatedAt = :now
          WHERE n.status = com.moneykk.moneytown.analysis.notification.domain.NotificationStatus.PENDING
            and n.isDeleted = false
            and n.createdAt < :threshold
    """)
    int failStaleProcessing(@Param("message") String message,
                            @Param("now") Instant now,
                            @Param("threshold") Instant threshold);

    // id + status 를 조건으로 건 원자적 완료 처리. PENDING이 아니면(리퍼가 먼저 처리했거나 중복 호출) 0건 반환.
    @Modifying(clearAutomatically = true)
    @Query("""
        update Notification n
           set n.status = com.moneykk.moneytown.analysis.notification.domain.NotificationStatus.SENT,
               n.sentAt = :now,
               n.updatedAt = :now
         where n.id = :id
           and n.status = com.moneykk.moneytown.analysis.notification.domain.NotificationStatus.PENDING
           and n.isDeleted = false
    """)
    int completeIfPending(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
        update Notification n
           set n.status = com.moneykk.moneytown.analysis.notification.domain.NotificationStatus.FAILED,
               n.errorMessage = :message,
               n.updatedAt = :now
         where n.id = :id
           and n.status = com.moneykk.moneytown.analysis.notification.domain.NotificationStatus.PENDING
           and n.isDeleted = false
    """)
    int failIfPending(@Param("id") UUID id, @Param("message") String message, @Param("now") Instant now);
}
