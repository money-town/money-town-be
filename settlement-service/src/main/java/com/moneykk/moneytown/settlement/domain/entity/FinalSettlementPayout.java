package com.moneykk.moneytown.settlement.domain.entity;

import com.moneykk.moneytown.common.entity.BaseUpdatableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "p_final_settlement_payouts",
        uniqueConstraints = @UniqueConstraint(name = "uk_final_settlement_payouts_batch_investor",
                columnNames = {"final_settlement_batch_id", "investor_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinalSettlementPayout extends BaseUpdatableEntity {

    @Id
    @Column(name = "final_settlement_payout_id")
    private UUID id;

    @Column(name = "final_settlement_batch_id", nullable = false)
    private UUID finalSettlementBatchId;

    @Column(name = "investor_id", nullable = false)
    private UUID investorId;

    @Column(name = "quantity", nullable = false)
    private Long quantity;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PayoutStatus status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    private FinalSettlementPayout(UUID finalSettlementBatchId, UUID investorId, Long quantity, Long amount) {
        this.id = UUID.randomUUID();
        this.finalSettlementBatchId = finalSettlementBatchId;
        this.investorId = investorId;
        this.quantity = quantity;
        this.amount = amount;
        this.idempotencyKey = this.id.toString();
        this.status = PayoutStatus.QUEUED;
        this.retryCount = 0;
    }

    public static FinalSettlementPayout queue(UUID finalSettlementBatchId, UUID investorId, Long quantity, Long amount) {
        return new FinalSettlementPayout(finalSettlementBatchId, investorId, quantity, amount);
    }

    public void requeue() {
        this.status = PayoutStatus.QUEUED;
        this.retryCount = 0;
    }

    public void markPaid() {
        this.status = PayoutStatus.PAID;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public void markRetrying() {
        this.status = PayoutStatus.RETRYING;
    }

    public void markDeadLetter() {
        this.status = PayoutStatus.DEAD_LETTER;
    }
}