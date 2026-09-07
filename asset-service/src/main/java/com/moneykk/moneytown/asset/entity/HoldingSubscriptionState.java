package com.moneykk.moneytown.asset.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** 늦게 도착한 지분 배정을 차단하기 위한 청약별 처리 상태 */
@Getter
@Entity
@Table(name = "p_holding_subscription_states")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HoldingSubscriptionState {

    @Id
    @Column(name = "subscription_id", nullable = false, updatable = false)
    private UUID subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private HoldingSubscriptionStatus status;

    @Column(name = "holding_id")
    private UUID holdingId;

    @Column(name = "block_reason", length = 500)
    private String blockReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public HoldingSubscriptionState(UUID subscriptionId) {
        Instant now = Instant.now();
        this.subscriptionId = subscriptionId;
        this.status = HoldingSubscriptionStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean blocksAllocation() {
        return status == HoldingSubscriptionStatus.BLOCKED
                || status == HoldingSubscriptionStatus.REVOKED;
    }

    public boolean isBlocked() {
        return status == HoldingSubscriptionStatus.BLOCKED;
    }

    public void markBlocked(String reason) {
        status = HoldingSubscriptionStatus.BLOCKED;
        blockReason = reason;
        updatedAt = Instant.now();
    }

    public void markAllocated(UUID holdingId) {
        status = HoldingSubscriptionStatus.ALLOCATED;
        this.holdingId = holdingId;
        blockReason = null;
        updatedAt = Instant.now();
    }

    public void markRevoked(UUID holdingId) {
        status = HoldingSubscriptionStatus.REVOKED;
        this.holdingId = holdingId;
        blockReason = null;
        updatedAt = Instant.now();
    }
}
