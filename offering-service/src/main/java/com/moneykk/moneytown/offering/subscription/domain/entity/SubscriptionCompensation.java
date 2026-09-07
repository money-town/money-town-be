package com.moneykk.moneytown.offering.subscription.domain.entity;

import com.moneykk.moneytown.common.entity.BaseEntity;
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
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "p_subscription_compensations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_subscription_compensations_subscription",
                        columnNames = "subscription_id"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionCompensation extends BaseEntity {

    @Id
    @Column(name = "compensation_id", nullable = false, updatable = false)
    private UUID compensationId;

    @Column(name = "subscription_id", nullable = false, updatable = false)
    private UUID subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "wallet_status", nullable = false, length = 20)
    private CompensationStatus walletStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "holding_status", nullable = false, length = 20)
    private CompensationStatus holdingStatus;

    @Column(name = "wallet_error_code", length = 100)
    private String walletErrorCode;

    @Column(name = "holding_error_code", length = 100)
    private String holdingErrorCode;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    private SubscriptionCompensation(UUID subscriptionId) {
        this.subscriptionId = Objects.requireNonNull(
                subscriptionId,
                "subscriptionId는 필수입니다."
        );
        this.compensationId = UUID.randomUUID();
        this.walletStatus = CompensationStatus.PENDING;
        this.holdingStatus = CompensationStatus.PENDING;
    }

    /**
     * 청약의 보상 진행 정보를 최초 생성한다.
     * 재시도 시에는 새로 생성하지 않고 기존 엔티티를 사용한다.
     */
    public static SubscriptionCompensation create(UUID subscriptionId) {
        return new SubscriptionCompensation(subscriptionId);
    }

    /**
     * 검증된 Wallet 보상 성공 결과를 반영한다.
     *
     * RELEASE / REFUND / NONE 성공 결과에 사용한다.
     * 실패 이후 성공 결과가 도착한 경우에도 성공으로 전환한다.
     */
    public void markWalletSucceeded() {
        this.walletStatus = CompensationStatus.SUCCEEDED;
        this.walletErrorCode = null;
    }

    /**
     * Wallet 보상 실패 결과를 반영한다.
     *
     * 이미 성공한 단계는 늦게 도착한 실패 결과로 되돌리지 않는다.
     */
    public void markWalletFailed(String errorCode) {
        String validatedErrorCode = validateErrorCode(errorCode);

        if (walletStatus == CompensationStatus.SUCCEEDED) {
            return;
        }

        this.walletStatus = CompensationStatus.FAILED;
        this.walletErrorCode = validatedErrorCode;
    }

    /**
     * 검증된 Holding 회수 성공 결과를 반영한다.
     *
     * REVOKED / NO_ACTION 성공 결과에 사용한다.
     * 지분 배정 성공 결과에는 사용하지 않는다.
     */
    public void markHoldingSucceeded() {
        this.holdingStatus = CompensationStatus.SUCCEEDED;
        this.holdingErrorCode = null;
    }

    /**
     * Holding 회수 실패 결과를 반영한다.
     *
     * 이미 성공한 단계는 늦게 도착한 실패 결과로 되돌리지 않는다.
     */
    public void markHoldingFailed(String errorCode) {
        String validatedErrorCode = validateErrorCode(errorCode);

        if (holdingStatus == CompensationStatus.SUCCEEDED) {
            return;
        }

        this.holdingStatus = CompensationStatus.FAILED;
        this.holdingErrorCode = validatedErrorCode;
    }

    /**
     * 외부 서비스의 보상이 모두 완료됐는지 확인한다.
     *
     * Offering의 확보 수량 복원 여부는 포함하지 않는다.
     * 최종 청약 취소 여부는 서비스에서 quantityReserved와 함께 판단한다.
     */
    public boolean isExternalCompensationCompleted() {
        return walletStatus == CompensationStatus.SUCCEEDED
                && holdingStatus == CompensationStatus.SUCCEEDED;
    }

    private static String validateErrorCode(String errorCode) {
        if (errorCode == null
                || errorCode.isBlank()
                || errorCode.length() > 100) {
            throw new IllegalArgumentException(
                    "errorCode는 비어 있을 수 없고 100자를 초과할 수 없습니다."
            );
        }

        return errorCode;
    }
}