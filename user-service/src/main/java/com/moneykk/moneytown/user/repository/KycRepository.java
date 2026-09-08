package com.moneykk.moneytown.user.repository;

import com.moneykk.moneytown.user.entity.Kyc;
import com.moneykk.moneytown.user.entity.type.KycVerificationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KycRepository extends JpaRepository<Kyc, UUID> {
    // 최근 KYC 신청 조회
    Optional<Kyc> findFirstByUserIdAndIsDeletedFalseOrderByAttemptNoDesc(
            UUID userId
    );

    // KYC 신청 이력 조회
    List<Kyc> findAllByUserIdAndIsDeletedFalseOrderByAttemptNoDesc(UUID userId);

    // 심사 중인 KYC 신청 확인
    boolean existsByUserIdAndStatusAndIsDeletedFalse(UUID userId, KycVerificationStatus status);

    // 전체 KYC 심사 목록
    Page<Kyc> findAllByIsDeletedFalse(Pageable pageable);

    // 상태별 KYC 심사 목록
    Page<Kyc> findAllByStatusAndIsDeletedFalse(KycVerificationStatus status, Pageable pageable);




    // 심사 대상 잠금 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT k
        FROM Kyc k
        WHERE k.id = :kycId
          AND k.isDeleted = false
        """)
    Optional<Kyc> findByIdForUpdate(@Param("kycId") UUID kycId);

    // 심사 대상의 사용자 ID 조회
    @Query("""
        SELECT k.userId
        FROM Kyc k
        WHERE k.id = :kycId
          AND k.isDeleted = false
        """)
    Optional<UUID> findUserIdByKycId(@Param("kycId") UUID kycId);

    // 삭제 이력을 포함한 마지막 신청 회차
    @Query("""
        SELECT COALESCE(MAX(k.attemptNo), 0)
        FROM Kyc k
        WHERE k.userId = :userId
        """)
    int findMaxAttemptNoByUserId(@Param("userId") UUID userId);


    // 단건 조회
    Optional<Kyc> findByIdAndIsDeletedFalse(UUID kycId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Kyc> findTopByUserIdAndIsDeletedFalseOrderByAttemptNoDesc(UUID userId);
}
