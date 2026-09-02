package com.moneykk.moneytown.offering.subscription.domain.entity;

import com.moneykk.moneytown.common.entity.BaseUpdatableEntity;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "p_subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription extends BaseUpdatableEntity {

    /**
     * 청약 식별자.
     */
    @Id
    @Column(name = "subscription_id", nullable = false, updatable = false)
    private UUID subscriptionId;

    /**
     * 청약 대상 공모 ID.
     *
     * Offering Service 내부의 p_offerings.offering_id를 참조한다.
     *
     * 현재는 MSA/CQRS 구조와 단순 ID 참조 방식을 고려하여
     * Offering과 JPA 연관관계를 맺지 않고 식별자만 저장한다.(@ManyToOne 없음)
     *
     *  Entity 간 결합도를 낮게 유지하면서
     *  DB Foreign Key를 통해 참조 무결성을 보장한다.
     */
    @Column(name = "offering_id", nullable = false, updatable = false)
    private UUID offeringId;

    /**
     * 청약 투자자 ID.
     *
     * JWT에서 식별한 사용자 ID를 저장하며,
     * User Service와 DB FK를 직접 연결하지 않는다.
     */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /**
     * 투자자가 신청한 청약 수량.
     * 청약자가 몇 조각을 청약할지 직접 입력하는 값
     */
    @Column(name = "quantity", nullable = false)
    private Long quantity;

    /**
     * 청약 접수 시점의 조각당 가격.
     *
     * 공모의 pricePerUnit을 Snapshot으로 저장하여
     * 이후 공모 데이터가 변경되더라도 청약 당시 가격을 보존한다.
     */
    @Column(name = "price_per_unit", nullable = false)
    private Long pricePerUnit;

    /**
     * 청약 신청 총 금액.
     *
     * pricePerUnit * quantity로 서버에서 계산한다.
     */
    @Column(name = "amount", nullable = false)
    private Long amount;

    /**
     * 현재 청약 처리 상태.
     *
     * 최초 청약 접수 시 PROCESSING 상태로 생성된다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false, length = 30)
    private SubscriptionStatus subscriptionStatus;

    /**
     * 공모의 잔여 수량을 성공적으로 확보했는지 여부.
     *
     * 수량 확보 성공 후 생성되는 청약은 true이며,
     * 수량 복원 완료 후 false로 변경한다.
     */
    @Column(name = "quantity_reserved", nullable = false)
    private boolean quantityReserved;

    /**
     * 청약 처리 실패 코드.
     *
     * 시스템 처리 실패 및 운영 추적이 필요한 경우 사용한다.
     */
    @Column(name = "failure_code", length = 50)
    private String failureCode;

    /**
     * 청약 취소가 최종 완료된 시각.
     */
    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /**
     * 청약 취소 유형.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_type", length = 50)
    private CancellationType cancellationType;

    /**
     * PROCESSING 상태의 수량 확보가 유효한 제한 시각.
     *
     * 장시간 PROCESSING 상태로 남아 있는 청약을 탐지하기 위해 사용한다.
     */
    // TODO 몇 분 뒤로 설정할지 아직 정책 없음
    @Column(name = "reservation_expires_at")
    private Instant reservationExpiresAt;

    /**
     * 청약 처리가 최종 확정된 시각.
     *
     * CONFIRMED 상태로 전환될 때 기록한다.
     */
    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    private Subscription(
            UUID subscriptionId,
            UUID offeringId,
            UUID userId,
            Long quantity,
            Long pricePerUnit,
            Long amount,
            SubscriptionStatus subscriptionStatus,
            boolean quantityReserved,
            Instant reservationExpiresAt
    ) {
        this.subscriptionId = subscriptionId;
        this.offeringId = offeringId;
        this.userId = userId;
        this.quantity = quantity;
        this.pricePerUnit = pricePerUnit;
        this.amount = amount;
        this.subscriptionStatus = subscriptionStatus;
        this.quantityReserved = quantityReserved;
        this.reservationExpiresAt = reservationExpiresAt;
    }

    /**
     * 선착순 수량 확보에 성공한 신규 청약을 생성한다.
     *
     * 청약 금액은 클라이언트 입력을 사용하지 않고
     * 청약 시점의 공모 단위 가격과 신청 수량을 이용하여 서버에서 계산한다.
     */
    public static Subscription create(
            UUID offeringId,
            UUID userId,
            Long quantity,
            Long pricePerUnit,
            Instant reservationExpiresAt
    ) {
        validateCreate(
                offeringId,
                userId,
                quantity,
                pricePerUnit
        );

        long amount = Math.multiplyExact(pricePerUnit, quantity);

        return new Subscription(
                UUID.randomUUID(),
                offeringId,
                userId,
                quantity,
                pricePerUnit,
                amount,
                SubscriptionStatus.PROCESSING,
                true,
                reservationExpiresAt
        );
    }

    private static void validateCreate(
            UUID offeringId,
            UUID userId,
            Long quantity,
            Long pricePerUnit
    ) {
        if (offeringId == null) {
            throw new IllegalArgumentException(
                    "공모 ID는 필수입니다."
            );
        }
        // TODO 2차 구현범위에서 아래 내용 재검토 예정
        if (userId == null) {
            throw new IllegalArgumentException(
                    "사용자 ID는 필수입니다."
            );
        }
        // 클라이언트 입력 검증
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException(
                    "청약 수량은 1 이상이어야 합니다."
            );
        }
        /*
         * Offering에서 전달받은 단위 가격 Snapshot을 검증한다.
         * 각 Aggregate는 외부에서 전달받은 값에 의존하지 않고
         * 자신의 도메인 불변식을 독립적으로 검증한다.
         *
         * Asset.unitPrice
         *   → Offering.pricePerUnit
         *   → Subscription.pricePerUnit
         */
        if (pricePerUnit == null || pricePerUnit <= 0) {
            throw new IllegalArgumentException(
                    "청약 단위 가격은 0보다 커야 합니다."
            );
        }
    }
}