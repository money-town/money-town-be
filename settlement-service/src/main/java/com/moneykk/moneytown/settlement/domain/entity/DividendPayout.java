package com.moneykk.moneytown.settlement.domain.entity;

import com.moneykk.moneytown.common.entity.BaseUpdatableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Entity
@Table(name = "p_dividend_payouts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DividendPayout extends BaseUpdatableEntity {

    @Id
    @Column(name = "dividend_payout_id")
    private UUID id;

    @Column(name = "settlement_batch_id", nullable = false)
    private UUID settlementBatchId;

    @Column(name = "investor_id", nullable = false)
    private UUID investorId;

    @Column(name = "share_ratio", nullable = false, precision = 10, scale = 8)
    private BigDecimal shareRatio;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PayoutStatus status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    private DividendPayout(UUID settlementBatchId, UUID investorId, BigDecimal shareRatio, Long amount) {
        this.id = UUID.randomUUID();
        this.settlementBatchId = settlementBatchId;
        this.investorId = investorId;
        this.shareRatio = shareRatio;
        this.amount = amount;
        this.idempotencyKey = this.id.toString();
        this.status = PayoutStatus.QUEUED;
        this.retryCount = 0;
    }

    public static DividendPayout queue(UUID settlementBatchId, UUID investorId, BigDecimal shareRatio, Long amount) {
        return new DividendPayout(settlementBatchId, investorId, shareRatio, amount);
    }
}