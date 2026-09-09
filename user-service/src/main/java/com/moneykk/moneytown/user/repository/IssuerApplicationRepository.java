package com.moneykk.moneytown.user.repository;

import com.moneykk.moneytown.user.entity.IssuerApplication;
import com.moneykk.moneytown.user.entity.type.IssuerApplicationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface IssuerApplicationRepository
        extends JpaRepository<IssuerApplication, UUID> {

    boolean existsByUserIdAndStatusAndIsDeletedFalse(
            UUID userId,
            IssuerApplicationStatus status
    );

    Optional<IssuerApplication>
    findFirstByUserIdAndIsDeletedFalseOrderByAppliedAtDesc(UUID userId);

    Page<IssuerApplication> findAllByIsDeletedFalse(Pageable pageable);

    Page<IssuerApplication> findAllByStatusAndIsDeletedFalse(
            IssuerApplicationStatus status,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT i
        FROM IssuerApplication i
        WHERE i.issuerApplicationId = :applicationId
          AND i.isDeleted = false
        """)
    Optional<IssuerApplication> findByIdForUpdate(
            @Param("applicationId") UUID applicationId
    );

    @Query("""
        SELECT i.userId
        FROM IssuerApplication i
        WHERE i.issuerApplicationId = :applicationId
          AND i.isDeleted = false
        """)
    Optional<UUID> findUserIdByApplicationId(
            @Param("applicationId") UUID applicationId
    );
}
