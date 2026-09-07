package com.moneykk.moneytown.user.repository;

import com.moneykk.moneytown.user.entity.Kyc;
import com.moneykk.moneytown.user.entity.type.KycVerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface KycRepository extends JpaRepository<Kyc, UUID> {
    // 가장 최근 신청 조회
    Optional<Kyc> findFirstByUserIdAndIsDeletedFalseOrderByAttemptNoDesc(UUID userId);

    // 심사 중인 신청 존재 확인
    boolean existsByUserIdAndStatusAndIsDeletedFalse(
            UUID userId,
            KycVerificationStatus status
    );



}
