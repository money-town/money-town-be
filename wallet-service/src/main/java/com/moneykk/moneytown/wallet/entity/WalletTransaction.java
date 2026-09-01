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

// append-only 원장 — 생성 후 절대 수정하지 않는다 (updated_at/is_deleted 없음).
@Getter
@Entity
@Table(name = "p_wallet_transactions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletTransaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private Long walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20, updatable = false)
    private WalletTransactionType type;

    @Column(name = "amount", nullable = false, updatable = false)
    private long amount;

    @Column(name = "balance_before", nullable = false, updatable = false)
    private long balanceBefore;

    @Column(name = "balance_after", nullable = false, updatable = false)
    private long balanceAfter;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "reference_id", updatable = false, length = 100)
    private String referenceId;

    public WalletTransaction(Long walletId, WalletTransactionType type, long amount,
                              long balanceBefore, long balanceAfter,
                              String idempotencyKey, String referenceId) {
        if (amount <= 0) {
            throw new BusinessException(WalletErrorCode.INVALID_AMOUNT);
        }
        requireConsistentBalanceSnapshot(type, amount, balanceBefore, balanceAfter);
        this.walletId = walletId;
        this.type = type;
        this.amount = amount;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.idempotencyKey = idempotencyKey;
        this.referenceId = referenceId;
    }

    // 총잔액(balance) 기준 방향: DEPOSIT/DIVIDEND/REFUND/SETTLEMENT는 증가, WITHDRAW/DEDUCT는 감소,
    // HOLD/UNHOLD는 hold_balance만 움직이고 balance는 불변.
    private static void requireConsistentBalanceSnapshot(WalletTransactionType type, long amount,
                                                           long balanceBefore, long balanceAfter) {
        boolean isDecreasing = type == WalletTransactionType.WITHDRAW || type == WalletTransactionType.DEDUCT;
        if (balanceBefore < 0 || (isDecreasing && amount > balanceBefore)) {
            throw new BusinessException(WalletErrorCode.INVALID_TRANSACTION_BALANCE_SNAPSHOT);
        }
        long expectedAfter = switch (type) {
            case DEPOSIT, DIVIDEND, REFUND, SETTLEMENT -> balanceBefore + amount;
            case WITHDRAW, DEDUCT -> balanceBefore - amount;
            case HOLD, UNHOLD -> balanceBefore;
        };
        if (balanceAfter != expectedAfter) {
            throw new BusinessException(WalletErrorCode.INVALID_TRANSACTION_BALANCE_SNAPSHOT);
        }
    }
}
