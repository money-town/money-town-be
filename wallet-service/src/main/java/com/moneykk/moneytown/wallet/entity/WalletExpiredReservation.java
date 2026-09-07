package com.moneykk.moneytown.wallet.entity;

import com.moneykk.moneytown.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

// 청약 타임아웃(reason=RESERVATION_EXPIRED) 보상 요청 시점에 HOLD가 없던 청약을 기록하는 tombstone.
// append-only라 BaseEntity(생성 정보만)를 상속.
@Getter
@Entity
@Table(name = "p_wallet_expired_reservations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletExpiredReservation extends BaseEntity {

    @Id
    @Column(name = "subscription_id", nullable = false, updatable = false)
    private UUID subscriptionId;

    @Column(name = "reason", nullable = false, updatable = false, length = 50)
    private String reason;

    public WalletExpiredReservation(UUID subscriptionId, String reason) {
        this.subscriptionId = subscriptionId;
        this.reason = reason;
    }
}
