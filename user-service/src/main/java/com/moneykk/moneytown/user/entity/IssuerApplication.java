package com.moneykk.moneytown.user.entity;

import com.moneykk.moneytown.common.entity.BaseUpdatableEntity;
import com.moneykk.moneytown.user.entity.type.IssuerApplicationStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "p_issuer_applications",
        indexes = {
                @Index(
                        name = "idx_p_issuer_applications_user_status",
                        columnList = "user_id, status"
                )
        }
)
public class IssuerApplication extends BaseUpdatableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "issuer_application_id", nullable = false, updatable = false)
    private UUID issuerApplicationId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "application_reason", nullable = false, length = 500)
    private String applicationReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private IssuerApplicationStatus status;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    public static IssuerApplication create(
            UUID userId,
            String applicationReason
    ) {
        IssuerApplication application = new IssuerApplication();
        application.userId = userId;
        application.applicationReason = applicationReason;
        application.status = IssuerApplicationStatus.PENDING;
        application.appliedAt = Instant.now();

        return application;
    }

    public boolean isPending() {
        return this.status == IssuerApplicationStatus.PENDING;
    }

    public void approve(UUID adminId) {
        this.status = IssuerApplicationStatus.APPROVED;
        this.reviewedBy = adminId;
        this.reviewedAt = Instant.now();
        this.rejectionReason = null;
    }

    public void reject(UUID adminId, String rejectionReason) {
        this.status = IssuerApplicationStatus.REJECTED;
        this.reviewedBy = adminId;
        this.reviewedAt = Instant.now();
        this.rejectionReason = rejectionReason;
    }
}
