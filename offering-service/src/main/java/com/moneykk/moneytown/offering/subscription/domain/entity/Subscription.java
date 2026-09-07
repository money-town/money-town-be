package com.moneykk.moneytown.offering.subscription.domain.entity;

import com.moneykk.moneytown.common.entity.BaseUpdatableEntity;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
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
    @Column(name = "reservation_expires_at")
    private Instant reservationExpiresAt;

    /**
     * 청약 처리가 최종 확정된 시각.
     *
     * CONFIRMED 상태로 전환될 때 기록한다.
     */
    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    /**
     * 확정된 청약의 Holding 지분 배정 후처리 상태.
     *
     * 청약 확정 전에는 null이며, 확정 시 PENDING으로 시작한다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "holding_allocation_status", length = 20)
    private HoldingAllocationStatus holdingAllocationStatus;

    /**
     * Holding 지분 배정의 최근 실패 코드.
     *
     * 배정 성공 시 null로 초기화한다.
     */
    @Column(name = "holding_allocation_error_code", length = 100)
    private String holdingAllocationErrorCode;

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
                pricePerUnit,
                reservationExpiresAt
        );

        long amount = calculateAmount(pricePerUnit, quantity);

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

    /**
     * Wallet 동결 성공을 확인한 청약을 확정한다.
     *
     * 수량이 확보된 PROCESSING 청약만 확정할 수 있다.
     * 중복 이벤트와 늦게 도착한 이벤트의 처리는
     * 호출하는 서비스에서 잠금 조회 후 판단한다.
     *
     * 동결 성공: PROCESSING -> CONFIRMED
     * CONFIRMED 전환 시 confirmedAt 기록
     *
     * @param confirmedAt 청약 확정 처리 시각
     */
    public void confirm(Instant confirmedAt) {
        if (confirmedAt == null) {
            throw new BusinessException(
                    SubscriptionErrorCode.INVALID_SUBSCRIPTION_INPUT
            );
        }

        if (subscriptionStatus != SubscriptionStatus.PROCESSING
                || !quantityReserved) {
            throw new BusinessException(
                    SubscriptionErrorCode.SUBSCRIPTION_CONFIRMATION_NOT_ALLOWED
            );
        }

        this.subscriptionStatus = SubscriptionStatus.CONFIRMED;
        this.confirmedAt = confirmedAt;
        this.holdingAllocationStatus = HoldingAllocationStatus.PENDING;
        this.holdingAllocationErrorCode = null;
    }

    /**
     * Holding 지분 배정 성공을 기록한다.
     *
     * ALLOCATED와 ALREADY_PROCESSED 결과에 공통으로 사용한다.
     */
    public void markHoldingAllocationSucceeded() {
        validateHoldingAllocationStarted();

        this.holdingAllocationStatus =
                HoldingAllocationStatus.SUCCEEDED;
        this.holdingAllocationErrorCode = null;
    }

    /**
     * Holding 지분 배정 실패를 기록한다.
     *
     * 청약의 업무 상태 자체는 변경하지 않는다.
     */
    public void markHoldingAllocationFailed(String errorCode) {
        validateHoldingAllocationStarted();

        if (errorCode == null
                || errorCode.isBlank()
                || errorCode.length() > 100) {
            throw new BusinessException(
                    SubscriptionErrorCode.INVALID_SUBSCRIPTION_INPUT
            );
        }

        /*
         * 성공 이후 다른 eventId의 늦은 실패가 도착해도
         * 완료된 지분 배정 상태를 실패로 되돌리지 않는다.
         */
        if (holdingAllocationStatus
                == HoldingAllocationStatus.SUCCEEDED) {
            return;
        }

        this.holdingAllocationStatus =
                HoldingAllocationStatus.FAILED;
        this.holdingAllocationErrorCode = errorCode;
    }

    /**
     * 지갑 동결 실패에 따른 수량 복원을 시작한다.
     *
     * 수량이 확보된 PROCESSING 청약만 처리할 수 있다.
     * reason은 WalletHoldFailed payload의 실패 사유다.
     */
    public void startHoldFailureCompensation(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 50) {
            throw new BusinessException(
                    SubscriptionErrorCode.INVALID_SUBSCRIPTION_INPUT
            );
        }

        if (subscriptionStatus != SubscriptionStatus.PROCESSING
                || !quantityReserved
                || cancellationType != null) {
            throw new BusinessException(
                    SubscriptionErrorCode.SUBSCRIPTION_HOLD_FAILURE_NOT_ALLOWED
            );
        }

        this.subscriptionStatus = SubscriptionStatus.COMPENSATING;
        this.failureCode = reason;
    }

    /**
     * 지갑 동결 실패에 따른 수량 복원을 마친 청약을 거절한다.
     *
     * 서비스에서 공모 수량 복원에 성공한 뒤 호출해야 한다.
     * 수량 복원과 이 상태 변경은 같은 트랜잭션에서 처리한다.
     */
    public void completeHoldFailureRejection() {
        if (subscriptionStatus != SubscriptionStatus.COMPENSATING
                || !quantityReserved
                || cancellationType != null
                || failureCode == null
                || failureCode.isBlank()) {
            throw new BusinessException(
                    SubscriptionErrorCode.SUBSCRIPTION_HOLD_FAILURE_NOT_ALLOWED
            );
        }

        this.quantityReserved = false;
        this.subscriptionStatus = SubscriptionStatus.REJECTED;
    }

    // TODO : 타임아웃 자동 보상, 관리자 공모 중단, 운영 재처리
    /**
     * 공모 취소에 따른 청약 보상을 시작한다.
     *
     * PROCESSING 또는 CONFIRMED 상태의 청약만
     * COMPENSATING 상태로 전환할 수 있다.
     */
    public void startCompensation(CancellationType cancellationType) {

        if (cancellationType == null) {
            throw new BusinessException(
                    SubscriptionErrorCode.INVALID_SUBSCRIPTION_INPUT
            );
        }

        if (subscriptionStatus != SubscriptionStatus.PROCESSING
                && subscriptionStatus != SubscriptionStatus.CONFIRMED) {
            throw new BusinessException(
                    SubscriptionErrorCode.SUBSCRIPTION_COMPENSATION_NOT_ALLOWED
            );
        }

        this.subscriptionStatus = SubscriptionStatus.COMPENSATING;
        this.cancellationType = cancellationType;
    }

    /**
     * 공모 취소 보상에 따른 확보 수량 복원 완료를 기록한다.
     *
     * 서비스에서 실제 공모 수량 복원에 성공한 뒤,
     * 같은 트랜잭션 안에서 호출해야 한다.
     */
    public void markCompensationQuantityRestored() {
        if (subscriptionStatus != SubscriptionStatus.COMPENSATING
                || cancellationType == null
                || !quantityReserved) {
            throw new BusinessException(
                    SubscriptionErrorCode.SUBSCRIPTION_COMPENSATION_NOT_ALLOWED
            );
        }

        this.quantityReserved = false;
    }

    /**
     * 공모 취소 보상을 완료하고 청약을 취소한다.
     *
     * 서비스에서 Wallet·Holding 보상 완료를 확인한 뒤 호출한다.
     * 기존 취소 유형과 확정 이력은 보존한다.
     */
    public void completeCancellation(Instant cancelledAt) {
        if (cancelledAt == null) {
            throw new BusinessException(
                    SubscriptionErrorCode.INVALID_SUBSCRIPTION_INPUT
            );
        }

        if (subscriptionStatus != SubscriptionStatus.COMPENSATING
                || cancellationType == null
                || quantityReserved) {
            throw new BusinessException(
                    SubscriptionErrorCode.SUBSCRIPTION_COMPENSATION_NOT_ALLOWED
            );
        }

        this.subscriptionStatus = SubscriptionStatus.CANCELLED;
        this.cancelledAt = cancelledAt;
    }

    /**
     * PROCESSING 상태의 청약 예약 유효시간이 만료되었는지 확인한다.
     */
    public boolean isReservationExpired(Instant now) {
        if (now == null) {
            throw new BusinessException(
                    SubscriptionErrorCode.INVALID_SUBSCRIPTION_INPUT
            );
        }

        return subscriptionStatus == SubscriptionStatus.PROCESSING
                && reservationExpiresAt != null
                && !reservationExpiresAt.isAfter(now);
    }

    /**
     * 예약 유효시간이 만료된 PROCESSING 청약을 보상 처리 상태로 전환한다.
     *
     * TODO 다음 PR: 타임아웃 자동 보상 및 수량 복원 연결.
     */
    public void startExpirationCompensation(Instant now) {
        if (!isReservationExpired(now)) {
            throw new BusinessException(
                    SubscriptionErrorCode.SUBSCRIPTION_COMPENSATION_NOT_ALLOWED
            );
        }

        this.subscriptionStatus = SubscriptionStatus.COMPENSATING;
        this.failureCode = "RESERVATION_EXPIRED";
    }

    /**
     * 늦은 동결 성공 등 자동 처리하기 어려운 상황을 수동 확인 대상으로 기록한다.
     * 기존 실패 사유, 취소 유형, 확정·취소 시각 및 수량 확보 여부는 보존한다.
     */
    public void requireManualReview(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 50) {
            throw new BusinessException(
                    SubscriptionErrorCode.INVALID_SUBSCRIPTION_INPUT
            );
        }

        this.subscriptionStatus = SubscriptionStatus.MANUAL_REVIEW;

        if (this.failureCode == null || this.failureCode.isBlank()) {
            this.failureCode = reason;
        }
    }

    /**
     * 청약 총 금액을 계산한다.
     *
     * 단위 가격과 청약 수량의 곱이 Long 범위를 초과하는 경우
     * 잘못된 청약 금액으로 처리한다.
     */
    private static long calculateAmount(
            Long pricePerUnit,
            Long quantity
    ) {
        try {
            return Math.multiplyExact(
                    pricePerUnit,
                    quantity
            );
        } catch (ArithmeticException e) {
            throw new BusinessException(
                    SubscriptionErrorCode.INVALID_SUBSCRIPTION_AMOUNT
            );
        }
    }

    /**
     * Holding 지분 배정 요청이 시작된 청약인지 확인한다.
     */
    private void validateHoldingAllocationStarted() {
        if (holdingAllocationStatus == null) {
            throw new BusinessException(
                    SubscriptionErrorCode.SUBSCRIPTION_CONFIRMATION_NOT_ALLOWED
            );
        }
    }

    private static void validateCreate(
            UUID offeringId,
            UUID userId,
            Long quantity,
            Long pricePerUnit,
            Instant reservationExpiresAt
    ) {
        if (offeringId == null) {
            throw new BusinessException(
                    SubscriptionErrorCode.INVALID_SUBSCRIPTION_INPUT
            );
        }

        if (userId == null) {
            throw new BusinessException(
                    SubscriptionErrorCode.INVALID_SUBSCRIPTION_INPUT
            );
        }
        // 클라이언트 입력 검증
        if (quantity == null || quantity <= 0) {
            throw new BusinessException(
                    SubscriptionErrorCode.INVALID_SUBSCRIPTION_QUANTITY
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
            throw new BusinessException(
                    SubscriptionErrorCode.INVALID_SUBSCRIPTION_INPUT
            );
        }

        if (reservationExpiresAt == null
                || !reservationExpiresAt.isAfter(Instant.now())) {
            throw new BusinessException(
                    SubscriptionErrorCode.INVALID_SUBSCRIPTION_INPUT
            );
        }
    }
}