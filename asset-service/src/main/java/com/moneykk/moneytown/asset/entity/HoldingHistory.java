package com.moneykk.moneytown.asset.entity;

import com.moneykk.moneytown.common.entity.BaseEntity;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "p_holding_histories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HoldingHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "history_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "holding_id", nullable = false, updatable = false)
    private UUID holdingId;

    @Column(name = "subscription_id", updatable = false)
    private UUID subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "history_type", nullable = false, length = 30)
    private HoldingHistoryType historyType;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    @Column(name = "balance_before", nullable = false)
    private long balanceBefore;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "reason", length = 500)
    private String reason;

    public HoldingHistory(UUID holdingId, UUID subscriptionId, HoldingHistoryType historyType,
                          long quantity, long balanceBefore, long balanceAfter,
                          String idempotencyKey, String reason) {
        if (quantity <= 0 || balanceBefore < 0 || balanceAfter < 0) {
            throw new BusinessException(AssetErrorCode.INVALID_HOLDING_HISTORY);
        }
        if ((historyType == HoldingHistoryType.ALLOCATE || historyType == HoldingHistoryType.REVOKE)
                && subscriptionId == null) {
            throw new BusinessException(AssetErrorCode.SUBSCRIPTION_REQUIRED);
        }
        if (historyType != HoldingHistoryType.ALLOCATE && (reason == null || reason.isBlank())) {
            throw new BusinessException(AssetErrorCode.HOLDING_HISTORY_REASON_REQUIRED);
        }
        this.holdingId = holdingId;
        this.subscriptionId = subscriptionId;
        this.historyType = historyType;
        this.quantity = quantity;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.idempotencyKey = idempotencyKey;
        this.reason = reason;
    }
}
