package com.moneykk.moneytown.asset.entity;

import com.moneykk.moneytown.common.entity.BaseEntity;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.common.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "p_holdings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Holding extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "holding_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    public Holding(UUID assetId, UUID userId, long quantity) {
        if (quantity < 0) {
            throw new BusinessException(AssetErrorCode.INVALID_HOLDING_QUANTITY);
        }
        this.assetId = assetId;
        this.userId = userId;
        this.quantity = quantity;
    }

    public void allocate(long quantity) {
        requirePositive(quantity);
        this.quantity = Math.addExact(this.quantity, quantity);
    }

    public void revoke(long quantity) {
        requirePositive(quantity);
        if (this.quantity < quantity) {
            throw new BusinessException(AssetErrorCode.INSUFFICIENT_HOLDING_QUANTITY);
        }
        this.quantity -= quantity;
    }

    public void adjust(long quantity) {
        if (quantity < 0) {
            throw new BusinessException(AssetErrorCode.INVALID_HOLDING_QUANTITY);
        }
        this.quantity = quantity;
    }

    private static void requirePositive(long quantity) {
        if (quantity <= 0) {
            throw new BusinessException(AssetErrorCode.INVALID_HOLDING_QUANTITY);
        }
    }
}
