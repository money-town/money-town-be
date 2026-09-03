package com.moneykk.moneytown.asset.entity;

import com.moneykk.moneytown.common.entity.BaseUpdatableEntity;
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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Entity
@Table(name = "p_assets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Asset extends BaseUpdatableEntity {

    private static final BigDecimal OWNER_ANNUAL_INTEREST_RATE = new BigDecimal("0.10");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "asset_name", nullable = false, length = 200)
    private String assetName;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 30)
    private AssetType type;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "valuation_amount", nullable = false)
    private long valuationAmount;

    @Column(name = "expected_return_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal expectedReturnRate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail_data", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> detailData = new HashMap<>();

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    @Column(name = "total_share_quantity", nullable = false)
    private long totalShareQuantity;

    @Column(name = "allocated_quantity", nullable = false)
    private long allocatedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_status", nullable = false, length = 30)
    private AssetStatus status;

    @Column(name = "representative_image_key", length = 500)
    private String representativeImageKey;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // 기존 자산의 납부 방식은 임의로 정하지 않고 NULL 유지
    @Enumerated(EnumType.STRING)
    @Column(name = "owner_burden_payment_method", length = 30)
    private OwnerBurdenPaymentMethod ownerBurdenPaymentMethod;

    @Column(name = "completed_offering_id")
    private UUID completedOfferingId;

    @Column(name = "offering_completed_at")
    private Instant offeringCompletedAt;

    // 공모 완료 시 확정한 차액 원금
    @Column(name = "owner_burden_principal")
    private Long ownerBurdenPrincipal;

    public Asset(UUID userId, String assetName, AssetType type, String description,
                 long valuationAmount, BigDecimal expectedReturnRate,
                 Map<String, Object> detailData, long totalShareQuantity) {
        // 평가 금액과 수량은 양수, 지분 단가는 최소 1원
        if (valuationAmount <= 0 || totalShareQuantity <= 0
                || valuationAmount < totalShareQuantity) {
            throw new BusinessException(AssetErrorCode.INVALID_ASSET_SHARE_PRICE);
        }
        this.userId = userId;
        this.assetName = assetName;
        this.type = type;
        this.description = description;
        this.valuationAmount = valuationAmount;
        this.expectedReturnRate = expectedReturnRate;
        this.detailData = new HashMap<>(detailData);
        // 소수점 이하 버림, 남은 금액은 자산 소유주 부담
        this.unitPrice = valuationAmount / totalShareQuantity;
        this.totalShareQuantity = totalShareQuantity;
        this.allocatedQuantity = 0;
        this.status = AssetStatus.DRAFT;
    }

    /** 단가 절사로 발생한 소유주 부담금(원) */
    public long getOwnerBurdenAmount() {
        return valuationAmount - unitPrice * totalShareQuantity;
    }

    /** 자산 등록 시 소유주가 납부 방식 선택 */
    public void selectOwnerBurdenPaymentMethod(OwnerBurdenPaymentMethod method) {
        if (method == null) {
            throw new BusinessException(AssetErrorCode.OWNER_BURDEN_METHOD_REQUIRED);
        }
        if (status != AssetStatus.DRAFT || ownerBurdenPaymentMethod != null) {
            throw new BusinessException(AssetErrorCode.OWNER_BURDEN_CONFLICT);
        }
        this.ownerBurdenPaymentMethod = method;
    }

    /** 공모 성공 통지의 최초 완료 시각과 차액 원금 보존 */
    public void recordOfferingCompletion(UUID offeringId, Instant completedAt) {
        if (offeringId == null || completedAt == null) {
            throw new BusinessException(AssetErrorCode.INVALID_OFFERING_COMPLETION);
        }
        // PostgreSQL TIMESTAMPTZ 정밀도에 맞춰 재시도 비교
        completedAt = completedAt.truncatedTo(ChronoUnit.MICROS);
        // 재시도는 최초 시각을 유지하고, 다른 공모나 시각으로 덮어쓰기는 차단
        if (completedOfferingId != null) {
            if (completedOfferingId.equals(offeringId) && offeringCompletedAt.equals(completedAt)) {
                return;
            }
            throw new BusinessException(AssetErrorCode.OWNER_BURDEN_CONFLICT);
        }
        if (status != AssetStatus.APPROVED) {
            throw new BusinessException(AssetErrorCode.ASSET_NOT_AVAILABLE);
        }
        if (ownerBurdenPaymentMethod == null) {
            throw new BusinessException(AssetErrorCode.OWNER_BURDEN_METHOD_REQUIRED);
        }
        // 구버전 데이터의 단가 불일치로 잘못된 차액을 확정하지 않음
        if (unitPrice != valuationAmount / totalShareQuantity) {
            throw new BusinessException(AssetErrorCode.INVALID_ASSET_SHARE_PRICE);
        }
        this.ownerBurdenPrincipal = getOwnerBurdenAmount();
        this.completedOfferingId = offeringId;
        this.offeringCompletedAt = completedAt;
    }

    /** 공모 완료일부터 기준일까지 단리 이자 견적(실제 수납 처리는 아님) */
    public BigDecimal calculateOwnerBurdenInterest(LocalDate asOf) {
        if (asOf == null) {
            throw new BusinessException(AssetErrorCode.INVALID_OWNER_BURDEN_DATE);
        }
        if (ownerBurdenPaymentMethod != OwnerBurdenPaymentMethod.SALE_DEDUCTION
                || offeringCompletedAt == null) {
            return BigDecimal.ZERO;
        }
        LocalDate startDate = offeringCompletedAt.atZone(ZoneId.of("Asia/Seoul")).toLocalDate();
        long days = Math.max(0, ChronoUnit.DAYS.between(startDate, asOf));
        // 매일 절사하지 않고 누적 이자 계산 후 1원 미만 절사
        return BigDecimal.valueOf(ownerBurdenPrincipal)
                .multiply(OWNER_ANNUAL_INTEREST_RATE)
                .multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf(365), 0, RoundingMode.DOWN);
    }

    /** 지분 배정 */
    public void allocateShares(long quantity) {
        if (quantity <= 0) {
            throw new BusinessException(AssetErrorCode.INVALID_HOLDING_QUANTITY);
        }

        if (status != AssetStatus.APPROVED) {
            throw new BusinessException(AssetErrorCode.ASSET_NOT_AVAILABLE);
        }

        long remainingQuantity = totalShareQuantity - allocatedQuantity;

        if (quantity > remainingQuantity) {
            throw new BusinessException(AssetErrorCode.SHARE_QUANTITY_EXCEEDED);
        }

        allocatedQuantity += quantity;
    }

    /** 지분 회수 */
    public void revokeShares(long quantity) {
        if (quantity <= 0) {
            throw new BusinessException(
                    AssetErrorCode.INVALID_HOLDING_QUANTITY
            );
        }

        if (allocatedQuantity < quantity) {
            throw new BusinessException(
                    AssetErrorCode.INSUFFICIENT_ALLOCATED_QUANTITY
            );
        }

        allocatedQuantity -= quantity;
    }
}
