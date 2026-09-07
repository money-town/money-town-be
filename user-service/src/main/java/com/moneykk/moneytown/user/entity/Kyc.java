package com.moneykk.moneytown.user.entity;

import com.moneykk.moneytown.common.entity.BaseEntity;
import com.moneykk.moneytown.common.entity.BaseUpdatableEntity;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.user.entity.type.KycVerificationStatus;
import com.moneykk.moneytown.user.global.exception.KycErrorCode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@NoArgsConstructor
@Getter
@Table(name = "p_kyc_verifications")
public class Kyc extends BaseUpdatableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "kyc_verification_id", nullable = false)
    UUID id;

    @Column(name = "user_id", nullable = false)
    UUID userId;



    @Column(name = "occupation_type", nullable = false, length = 30)
    private String occupationType;

    @Column(name = "fund_source", nullable = false, length = 30)
    private String fundSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private KycVerificationStatus status = KycVerificationStatus.PENDING;

    @Column(name = "consent_version", nullable = false, length = 20)
    private String consentVersion;

    @Column(name = "domestic_resident", nullable = false)
    private boolean domesticResident;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo = 1;

    @Column(name = "consented_at", nullable = false)
    private Instant consentedAt;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    public static Kyc create( UUID userId,
                              String occupationType,
                              String fundSource,
                              String consentVersion,
                              boolean domesticResident,
                              int attemptNo){
        Kyc kyc = new Kyc();

        kyc.userId = userId;
        kyc.occupationType = occupationType;
        kyc.fundSource = fundSource;
        kyc.consentVersion = consentVersion;
        kyc.domesticResident = domesticResident;

        kyc.status = KycVerificationStatus.PENDING;
        kyc.attemptNo = attemptNo;
        kyc.consentedAt = Instant.now();
        kyc.submittedAt = Instant.now();

        return kyc;


    }

    // KYC 승인
    public void approve(UUID adminId, Instant expiresAt) {
        if (this.status != KycVerificationStatus.PENDING) {
            throw new BusinessException(
                    KycErrorCode.KYC_NOT_PENDING
            );
        }

        Instant now = Instant.now();

        if (expiresAt == null || !expiresAt.isAfter(now)) {
            throw new IllegalArgumentException(
                    "KYC 만료 시각은 현재 시각 이후여야 합니다."
            );
        }

        this.status = KycVerificationStatus.VERIFIED;
        this.reviewedBy = adminId;
        this.reviewedAt = now;
        this.verifiedAt = now;
        this.expiresAt = expiresAt;
        this.rejectionReason = null;
    }

    // KYC 거절
    public void reject(UUID adminId, String rejectionReason) {
        if (this.status != KycVerificationStatus.PENDING) {
            throw new BusinessException(
                    KycErrorCode.KYC_NOT_PENDING
            );
        }

        this.status = KycVerificationStatus.REJECTED;
        this.reviewedBy = adminId;
        this.reviewedAt = Instant.now();
        this.rejectionReason = rejectionReason;
        this.verifiedAt = null;
        this.expiresAt = null;
    }

    // KYC 이력 만료 처리
    public boolean expire(Instant now) {
        if (this.status != KycVerificationStatus.VERIFIED) {return false;}

        if (this.expiresAt == null || this.expiresAt.isAfter(now)) {return false;}

        this.status = KycVerificationStatus.EXPIRED;return true;}


}
