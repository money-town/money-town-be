package com.moneykk.moneytown.wallet.entity;

import com.moneykk.moneytown.common.entity.BaseUpdatableEntity;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.wallet.global.exception.WalletErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "p_wallets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet extends BaseUpdatableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wallet_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "balance", nullable = false)
    private long balance;

    @Column(name = "hold_balance", nullable = false)
    private long holdBalance;

    @Column(name = "available_balance", nullable = false)
    private long availableBalance;

    public Wallet(UUID userId) {
        this.userId = userId;
        this.balance = 0L;
        this.holdBalance = 0L;
        this.availableBalance = 0L;
    }

    public void deposit(long amount) {
        requirePositive(amount);
        this.balance += amount;
        this.availableBalance += amount;
    }

    public void withdraw(long amount) {
        requirePositive(amount);
        requireSufficientAvailableBalance(amount);
        this.balance -= amount;
        this.availableBalance -= amount;
    }

    public void hold(long amount) {
        requirePositive(amount);
        requireSufficientAvailableBalance(amount);
        this.holdBalance += amount;
        this.availableBalance -= amount;
    }

    public void releaseHold(long amount) {
        requirePositive(amount);
        this.holdBalance -= amount;
        this.availableBalance += amount;
    }

    public void deductHold(long amount) {
        requirePositive(amount);
        this.balance -= amount;
        this.holdBalance -= amount;
    }

    private void requireSufficientAvailableBalance(long amount) {
        if (this.availableBalance < amount) {
            throw new BusinessException(WalletErrorCode.INSUFFICIENT_AVAILABLE_BALANCE);
        }
    }

    private static void requirePositive(long amount) {
        if (amount <= 0) {
            throw new BusinessException(WalletErrorCode.INVALID_AMOUNT);
        }
    }
}
