package com.moneykk.moneytown.user.entity;

import com.moneykk.moneytown.common.entity.BaseEntity;
import com.moneykk.moneytown.common.entity.BaseUpdatableEntity;
import com.moneykk.moneytown.user.entity.type.KycVerificationStatus;
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


}
