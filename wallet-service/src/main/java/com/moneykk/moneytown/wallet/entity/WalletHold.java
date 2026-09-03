package com.moneykk.moneytown.wallet.entity;

import com.moneykk.moneytown.common.entity.BaseEntity;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.wallet.global.exception.WalletErrorCode;
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
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;
import java.util.UUID;

// HELD -> RELEASED 또는 HELD -> COMMITTED만 허용. 상태값이 종료를 표현하므로
// BaseUpdatableEntity의 소프트삭제 필드는 쓰지 않고 updated_at/by만 직접 선언.
@Getter
@Entity
@Table(name = "p_wallet_holds")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletHold extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hold_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private Long walletId;

    @Column(name = "subscription_id", nullable = false, updatable = false)
    private UUID subscriptionId;

    @Column(name = "amount", nullable = false, updatable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WalletHoldStatus status;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    public WalletHold(Long walletId, UUID subscriptionId, long amount) {
        if (amount <= 0) {
            throw new BusinessException(WalletErrorCode.INVALID_AMOUNT);
        }
        this.walletId = walletId;
        this.subscriptionId = subscriptionId;
        this.amount = amount;
        this.status = WalletHoldStatus.HELD;
    }

    public void release() {
        requireHeld();
        this.status = WalletHoldStatus.RELEASED;
    }

    public void commit() {
        requireHeld();
        this.status = WalletHoldStatus.COMMITTED;
    }

    private void requireHeld() {
        if (this.status != WalletHoldStatus.HELD) {
            throw new BusinessException(WalletErrorCode.INVALID_HOLD_STATUS_TRANSITION);
        }
    }
}
