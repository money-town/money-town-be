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

    // TODO 2차 구현범위에서 아래 내용 재검토 예정
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
     * TODO 추가 검증 - 심사요청 관련
     * 자산 상태가 여전히 APPROVED인지
     * 필수 첨부자료가 있는지
     * 심사 요청 가능한 기간인지
     * 기타 운영 검증
     *
     * */

    public void requestReview() {
        if (offeringStatus != OfferingStatus.DRAFT) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_REVIEW_REQUEST_NOT_ALLOWED
            );
        }

        validateForReview();
        validateOfferingPeriodNotExpired();

        this.offeringStatus = OfferingStatus.REVIEW_REQUESTED;
        this.reviewRequestedAt = Instant.now();
    }

    /**
     * 심사 요청된 공모를 승인한다.
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
        validateOfferingPeriodNotExpired();

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

        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new BusinessException(
                    OfferingErrorCode.INVALID_REJECTION_REASON
            );
        }

        if (rejectionReason.length() > 500) {
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
        this.rejectionReason = rejectionReason.trim();
        this.reviewedAt = Instant.now();
        this.reviewedBy = reviewerId;
    }

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

    private void validateOfferingPeriodNotExpired() {
        if (!endAt.isAfter(Instant.now())) {
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