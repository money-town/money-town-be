package com.moneykk.moneytown.asset.entity;

import com.moneykk.moneytown.common.entity.BaseEntity;
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
            throw new IllegalArgumentException("보유 수량은 0 이상이어야 합니다.");
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
            throw new IllegalArgumentException("보유 수량보다 많은 지분을 회수할 수 없습니다.");
        }
        this.quantity -= quantity;
    }

    public void adjust(long quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("보유 수량은 0 이상이어야 합니다.");
        }
        this.quantity = quantity;
    }

    private static void requirePositive(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("변동 수량은 0보다 커야 합니다.");
        }
    }
}
