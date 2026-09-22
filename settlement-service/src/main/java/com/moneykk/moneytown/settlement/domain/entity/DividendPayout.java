package com.moneykk.moneytown.settlement.domain.entity;

import com.moneykk.moneytown.common.entity.BaseUpdatableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Entity
@Table(name = "p_dividend_payouts",
        uniqueConstraints = @UniqueConstraint(name = "uk_dividend_payouts_batch_investor",
                columnNames = {"settlement_batch_id", "investor_id"}),
        // 실제 정의는 V15__add_dividend_payouts_investor_index.sql(WHERE is_deleted = false 부분 인덱스)이 유일한 근거다.
        // @Index는 partial index 조건을 표현할 수 없어 컬럼 구성만 문서화한 것
        indexes = @Index(name = "idx_dividend_payouts_investor",
                columnList = "investor_id, updated_at DESC, dividend_payout_id ASC"))
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

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_type", length = 20)
    private ResolutionType resolutionType;

    @Column(name = "resolution_reference", length = 200)
    private String resolutionReference;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    private DividendPayout(UUID settlementBatchId, UUID investorId, BigDecimal shareRatio, Long amount) {
        this.id = UUID.randomUUID();
        this.settlementBatchId = settlementBatchId;
        this.investorId = investorId;
        this.shareRatio = shareRatio;
        this.amount = amount;
        this.idempotencyKey = settlementBatchId + ":" + investorId;
        this.status = PayoutStatus.QUEUED;
        this.retryCount = 0;
    }

    public static DividendPayout queue(UUID settlementBatchId, UUID investorId, BigDecimal shareRatio, Long amount) {
        return new DividendPayout(settlementBatchId, investorId, shareRatio, amount);
    }

    public void requeue() {
        this.status = PayoutStatus.QUEUED;
        this.retryCount = 0;
    }

    public void markProcessing() {
        this.status = PayoutStatus.PROCESSING;
    }

    public void revertStalledProcessing() {
        this.status = PayoutStatus.QUEUED;
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

    // DEAD_LETTER 건을 관리자가 명시적으로 포기 처리
    // 관리자가 이미 다른 방법(은행 송금 등)으로 실제 지급을 완료한 뒤, 그 증빙(resolutionType/resolutionReference)을 남기는 호출
    public void abandon(ResolutionType resolutionType, String resolutionReference, String resolutionNote) {
        this.status = PayoutStatus.ABANDONED;
        this.resolutionType = resolutionType;
        this.resolutionReference = resolutionReference;
        this.resolutionNote = resolutionNote;
    }
}