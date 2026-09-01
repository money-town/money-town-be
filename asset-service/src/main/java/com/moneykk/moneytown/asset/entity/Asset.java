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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Entity
@Table(name = "p_assets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Asset extends BaseUpdatableEntity {

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

    public Asset(UUID userId, String assetName, AssetType type, String description,
                 long valuationAmount, BigDecimal expectedReturnRate,
                 Map<String, Object> detailData, long unitPrice, long totalShareQuantity) {
        this.userId = userId;
        this.assetName = assetName;
        this.type = type;
        this.description = description;
        this.valuationAmount = valuationAmount;
        this.expectedReturnRate = expectedReturnRate;
        this.detailData = new HashMap<>(detailData);
        this.unitPrice = unitPrice;
        this.totalShareQuantity = totalShareQuantity;
        this.allocatedQuantity = 0;
        this.status = AssetStatus.DRAFT;
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
}
