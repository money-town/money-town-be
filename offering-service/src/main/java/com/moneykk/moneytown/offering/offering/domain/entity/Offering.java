package com.moneykk.moneytown.offering.offering.domain.entity;

import com.moneykk.moneytown.common.entity.BaseUpdatableEntity;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "p_offerings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Offering extends BaseUpdatableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "offering_id", nullable = false, updatable = false)
    private UUID offeringId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "issuer_id", nullable = false)
    private UUID issuerId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "price_per_unit", nullable = false)
    private Long pricePerUnit;

    @Column(name = "total_quantity", nullable = false)
    private Long totalQuantity;

    @Column(name = "remaining_quantity", nullable = false)
    private Long remainingQuantity;

    @Column(name = "min_subscription_quantity", nullable = false)
    private Long minSubscriptionQuantity;

    @Column(name = "max_subscription_quantity", nullable = false)
    private Long maxSubscriptionQuantity;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "offering_status", nullable = false, length = 30)
    private OfferingStatus offeringStatus;

    @Column(name = "review_requested_at")
    private Instant reviewRequestedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_type", length = 30)
    private CancellationType cancellationType;

    private Offering(
            UUID assetId,
            UUID issuerId,
            String title,
            Long pricePerUnit,
            Long totalQuantity,
            Long minSubscriptionQuantity,
            Long maxSubscriptionQuantity,
            Instant startAt,
            Instant endAt
    ) {
        validateCreate(
                assetId,
                issuerId,
                title,
                pricePerUnit,
                totalQuantity,
                minSubscriptionQuantity,
                maxSubscriptionQuantity,
                startAt,
                endAt
        );

        this.assetId = assetId;
        this.issuerId = issuerId;
        this.title = title;
        this.pricePerUnit = pricePerUnit;
        this.totalQuantity = totalQuantity;
        this.remainingQuantity = totalQuantity;
        this.minSubscriptionQuantity = minSubscriptionQuantity;
        this.maxSubscriptionQuantity = maxSubscriptionQuantity;
        this.startAt = startAt;
        this.endAt = endAt;

        this.offeringStatus = OfferingStatus.DRAFT;
    }

    public static Offering create(
            UUID assetId,
            UUID issuerId,
            String title,
            Long pricePerUnit,
            Long totalQuantity,
            Long minSubscriptionQuantity,
            Long maxSubscriptionQuantity,
            Instant startAt,
            Instant endAt
    ) {
        return new Offering(
                assetId,
                issuerId,
                title,
                pricePerUnit,
                totalQuantity,
                minSubscriptionQuantity,
                maxSubscriptionQuantity,
                startAt,
                endAt
        );
    }

    /**
     * 작성 중인 공모를 심사 요청 상태로 전환한다.
     *
     * 심사 요청은 DRAFT 상태이면서
     * 공모 시작 시각 이전인 경우에만 가능하다.
     *
     * TODO: 심사 요청 정책 추가 구현
     * - Asset Service 연동 후 Asset 상태가 여전히 APPROVED인지 Service에서 검증
     * - 필수 첨부자료 존재 여부 검증
     * - 기타 심사 요청 운영 정책 검증
     */
    public void requestReview() {
        if (offeringStatus != OfferingStatus.DRAFT) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_REVIEW_REQUEST_NOT_ALLOWED
            );
        }

        validateForReview();
        validateBeforeOfferingStart();

        this.offeringStatus = OfferingStatus.REVIEW_REQUESTED;
        this.reviewRequestedAt = Instant.now();
    }

    /**
     * 심사 요청된 공모를 승인한다.
     *
     * 승인은 공모 시작 시각 이전까지만 가능하며,
     * 승인 완료 후 SCHEDULED 상태로 전환한다.
     * - 스케줄러 처리
     */
    public void approve(UUID reviewerId) {
        if (offeringStatus != OfferingStatus.REVIEW_REQUESTED) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_APPROVAL_NOT_ALLOWED
            );
        }

        if (reviewerId == null) {
            throw new BusinessException(
                    OfferingErrorCode.INVALID_OFFERING_INPUT
            );
        }

        validateForApproval();
        validateBeforeOfferingStart();

        this.offeringStatus = OfferingStatus.SCHEDULED;
        this.reviewedAt = Instant.now();
        this.reviewedBy = reviewerId;

        // 이전 반려 정보가 존재할 가능성에 대비해 승인 시 초기화
        this.rejectionReason = null;
    }

    /**
     * 심사 요청된 공모를 반려한다.
     */
    public void reject(
            UUID reviewerId,
            String rejectionReason
    ) {
        if (offeringStatus != OfferingStatus.REVIEW_REQUESTED) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_REJECTION_NOT_ALLOWED
            );
        }

        if (rejectionReason == null) {
            throw new BusinessException(
                    OfferingErrorCode.INVALID_REJECTION_REASON
            );
        }

        String trimmedReason = rejectionReason.trim();

        if (trimmedReason.isBlank()
                || trimmedReason.length() > 500) {
            throw new BusinessException(
                    OfferingErrorCode.INVALID_REJECTION_REASON
            );
        }

        if (reviewerId == null) {
            throw new BusinessException(
                    OfferingErrorCode.INVALID_OFFERING_INPUT
            );
        }

        this.offeringStatus = OfferingStatus.REJECTED;
        this.rejectionReason = trimmedReason;
        this.reviewedAt = Instant.now();
        this.reviewedBy = reviewerId;
    }

    /**
     * 작성 중인 공모 정보를 수정한다.
     *
     * 현재 정책에서는 DRAFT 상태에서만 수정할 수 있으므로,
     * totalQuantity 변경 시 remainingQuantity도 동일한 값으로 초기화한다.
     *
     * TODO:
     * REJECTED 상태 수정 및 재심사를 허용할 경우
     * remainingQuantity 초기화 정책을 다시 검토한다.
     */
    public void update(
            String title,
            Long totalQuantity,
            Long minSubscriptionQuantity,
            Long maxSubscriptionQuantity,
            Instant startAt,
            Instant endAt
    ) {
        if (offeringStatus != OfferingStatus.DRAFT) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_UPDATE_NOT_ALLOWED
            );
        }

        String newTitle =
                title != null ? title : this.title;

        Long newTotalQuantity =
                totalQuantity != null ? totalQuantity : this.totalQuantity;

        Long newMinSubscriptionQuantity =
                minSubscriptionQuantity != null
                        ? minSubscriptionQuantity
                        : this.minSubscriptionQuantity;

        Long newMaxSubscriptionQuantity =
                maxSubscriptionQuantity != null
                        ? maxSubscriptionQuantity
                        : this.maxSubscriptionQuantity;

        Instant newStartAt =
                startAt != null ? startAt : this.startAt;

        Instant newEndAt =
                endAt != null ? endAt : this.endAt;

        validateCreate(
                this.assetId,
                this.issuerId,
                newTitle,
                this.pricePerUnit,
                newTotalQuantity,
                newMinSubscriptionQuantity,
                newMaxSubscriptionQuantity,
                newStartAt,
                newEndAt
        );

        this.title = newTitle;
        this.totalQuantity = newTotalQuantity;
        this.remainingQuantity = newTotalQuantity;
        this.minSubscriptionQuantity = newMinSubscriptionQuantity;
        this.maxSubscriptionQuantity = newMaxSubscriptionQuantity;
        this.startAt = newStartAt;
        this.endAt = newEndAt;
    }

    /**
     * 작성 중인 공모를 논리 삭제한다.
     */
    public void delete(UUID deletedBy) {
        if (offeringStatus != OfferingStatus.DRAFT) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_DELETE_NOT_ALLOWED
            );
        }

        if (deletedBy == null) {
            throw new BusinessException(
                    OfferingErrorCode.INVALID_OFFERING_INPUT
            );
        }

        softDelete(deletedBy);
    }

    /**
     * 모집 미달로 공모 취소 보상 절차를 시작한다.
     *
     * 모집 종료 후 잔여 수량이 존재하는 OPEN 공모를
     * CANCELLING 상태로 전환할 수 있다.
     *
     * CLOSED 이후 동결 실패로 수량이 복원된 경우도 포함한다.
     *
     * 최종 CANCELLED 전환 및 cancelledAt 기록은
     * 필요한 보상이 모두 완료된 이후 수행한다.
     */
    public void startUnderSubscribedCancellation() {

        boolean allowedStatus =
                offeringStatus == OfferingStatus.OPEN
                        || offeringStatus == OfferingStatus.SOLD_OUT
                        || offeringStatus == OfferingStatus.CLOSED;

        if (!allowedStatus) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_CANCELLATION_NOT_ALLOWED
            );
        }

        if (endAt.isAfter(Instant.now())) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_CANCELLATION_NOT_ALLOWED
            );
        }

        if (remainingQuantity <= 0) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_QUANTITY_STATE_INVALID
            );
        }

        this.offeringStatus = OfferingStatus.CANCELLING;
        this.cancellationType = CancellationType.UNDER_SUBSCRIBED;
    }

    /**
     * 필요한 청약 보상이 모두 끝난 공모의 취소를 완료한다.
     *
     * 서비스에서 미해결 청약이 없는지 확인한 뒤 호출한다.
     * 기존 취소 사유는 유지한다.
     */
    public void completeCancellation(Instant cancelledAt) {
        if (cancelledAt == null) {
            throw new BusinessException(
                    OfferingErrorCode.INVALID_OFFERING_INPUT
            );
        }

        if (offeringStatus != OfferingStatus.CANCELLING
                || cancellationType == null) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_CANCELLATION_COMPLETION_NOT_ALLOWED
            );
        }

        // 공모 취소 완료 시 모든 확보 수량이 복원되어 있어야 한다.
        if (totalQuantity == null
                || !totalQuantity.equals(remainingQuantity)) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_QUANTITY_STATE_INVALID
            );
        }

        this.offeringStatus = OfferingStatus.CANCELLED;
        this.cancelledAt = cancelledAt;
    }

    /**
     * 심사 요청 및 승인은 공모 시작 시각 이전까지만 허용한다.
     *
     * startAt과 현재 시각이 동일하거나 이미 지난 경우
     * 더 이상 심사 요청/승인할 수 없다.
     */
    private void validateBeforeOfferingStart() {
        if (!startAt.isAfter(Instant.now())) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_PERIOD_EXPIRED
            );
        }
    }

    private void validateForApproval() {
        validateCreate(
                assetId,
                issuerId,
                title,
                pricePerUnit,
                totalQuantity,
                minSubscriptionQuantity,
                maxSubscriptionQuantity,
                startAt,
                endAt
        );

        if (!totalQuantity.equals(remainingQuantity)) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_QUANTITY_STATE_INVALID
            );
        }
    }

    private void validateForReview() {
        validateCreate(
                assetId,
                issuerId,
                title,
                pricePerUnit,
                totalQuantity,
                minSubscriptionQuantity,
                maxSubscriptionQuantity,
                startAt,
                endAt
        );
    }

    private static void validateCreate(
            UUID assetId,
            UUID issuerId,
            String title,
            Long pricePerUnit,
            Long totalQuantity,
            Long minSubscriptionQuantity,
            Long maxSubscriptionQuantity,
            Instant startAt,
            Instant endAt
    ) {
        if (assetId == null) {
            throw new BusinessException(
                    OfferingErrorCode.INVALID_OFFERING_INPUT
            );
        }

        if (issuerId == null) {
            throw new BusinessException(
                    OfferingErrorCode.INVALID_OFFERING_INPUT
            );
        }

        if (title == null || title.isBlank()) {
            throw new BusinessException(
                    OfferingErrorCode.INVALID_OFFERING_TITLE
            );
        }

        if (title.length() > 200) {
            throw new BusinessException(
                    OfferingErrorCode.INVALID_OFFERING_TITLE
            );
        }

        if (pricePerUnit == null || pricePerUnit <= 0) {
            throw new BusinessException(
                    OfferingErrorCode.INVALID_OFFERING_PRICE
            );
        }

        if (totalQuantity == null || totalQuantity <= 0) {
            throw new BusinessException(
                    OfferingErrorCode.INVALID_OFFERING_QUANTITY
            );
        }

        if (minSubscriptionQuantity == null || minSubscriptionQuantity < 1) {
            throw new BusinessException(
                    OfferingErrorCode.INVALID_SUBSCRIPTION_QUANTITY_RANGE
            );
        }

        if (maxSubscriptionQuantity == null
                || maxSubscriptionQuantity < minSubscriptionQuantity) {
            throw new BusinessException(
                    OfferingErrorCode.INVALID_SUBSCRIPTION_QUANTITY_RANGE
            );
        }

        if (maxSubscriptionQuantity > totalQuantity) {
            throw new BusinessException(
                    OfferingErrorCode.INVALID_SUBSCRIPTION_QUANTITY_RANGE
            );
        }

        if (startAt == null || endAt == null) {
            throw new BusinessException(
                    OfferingErrorCode.INVALID_OFFERING_PERIOD
            );
        }

        if (!startAt.isBefore(endAt)) {
            throw new BusinessException(
                    OfferingErrorCode.INVALID_OFFERING_PERIOD
            );
        }
    }
}