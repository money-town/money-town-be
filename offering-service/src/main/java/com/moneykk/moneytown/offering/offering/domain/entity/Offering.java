package com.moneykk.moneytown.offering.offering.domain.entity;

import com.moneykk.moneytown.common.entity.BaseUpdatableEntity;
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

import java.math.BigDecimal;
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

    @Column(name = "price_per_unit", nullable = false, precision = 19, scale = 2)
    private BigDecimal pricePerUnit;

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
            BigDecimal pricePerUnit,
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
            BigDecimal pricePerUnit,
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
            // TODO: OfferingErrorCode 적용 후 O004로 변경
            throw new IllegalStateException(
                    "DRAFT 상태의 공모만 심사를 요청할 수 있습니다."
            );
        }

        validateForReview();

        this.offeringStatus = OfferingStatus.REVIEW_REQUESTED;
        this.reviewRequestedAt = Instant.now();
    }

    /**
     * 심사 요청된 공모를 승인한다.
     */
    public void approve(UUID reviewerId) {
        // TODO: OfferingErrorCode 적용 후 O006으로 변경
        if (offeringStatus != OfferingStatus.REVIEW_REQUESTED) {
            throw new IllegalStateException(
                    "REVIEW_REQUESTED 상태의 공모만 승인할 수 있습니다."
            );
        }

        // TODO: OfferingErrorCode 적용 후 O001로 변경
        if (reviewerId == null) {
            throw new IllegalArgumentException(
                    "승인 처리자 ID는 필수입니다."
            );
        }

        validateForApproval();

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
        // TODO: OfferingErrorCode 적용 후 O008로 변경
        if (offeringStatus != OfferingStatus.REVIEW_REQUESTED) {
            throw new IllegalStateException(
                    "반려할 수 없는 공모 상태입니다."
            );
        }

        // TODO: OfferingErrorCode 적용 후 O007로 변경
        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new IllegalArgumentException(
                    "반려 사유를 입력해주세요."
            );
        }

        if (rejectionReason.length() > 500) {
            throw new IllegalArgumentException(
                    "반려 사유는 500자를 초과할 수 없습니다."
            );
        }

        if (reviewerId == null) {
            throw new IllegalArgumentException(
                    "심사 처리자 ID는 필수입니다."
            );
        }

        this.offeringStatus = OfferingStatus.REJECTED;
        this.rejectionReason = rejectionReason.trim();
        this.reviewedAt = Instant.now();
        this.reviewedBy = reviewerId;
    }

    public void update(
            String title,
            BigDecimal pricePerUnit,
            Long totalQuantity,
            Long minSubscriptionQuantity,
            Long maxSubscriptionQuantity,
            Instant startAt,
            Instant endAt
    ) {
        // TODO: OfferingErrorCode 적용 후 O014로 변경
        if (offeringStatus != OfferingStatus.DRAFT) {
            throw new IllegalStateException(
                    "DRAFT 상태의 공모만 수정할 수 있습니다."
            );
        }

        String newTitle =
                title != null ? title : this.title;

        BigDecimal newPricePerUnit =
                pricePerUnit != null ? pricePerUnit : this.pricePerUnit;

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
                newPricePerUnit,
                newTotalQuantity,
                newMinSubscriptionQuantity,
                newMaxSubscriptionQuantity,
                newStartAt,
                newEndAt
        );

        this.title = newTitle;
        this.pricePerUnit = newPricePerUnit;
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
        // TODO: OfferingErrorCode 적용 후 O015로 변경
        if (offeringStatus != OfferingStatus.DRAFT) {
            throw new IllegalStateException(
                    "현재 상태에서는 공모 상품을 삭제할 수 없습니다."
            );
        }

        if (deletedBy == null) {
            throw new IllegalArgumentException(
                    "삭제 처리자 ID는 필수입니다."
            );
        }

        softDelete(deletedBy);
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
            throw new IllegalArgumentException(
                    "승인 전 잔여 모집 수량은 총 모집 수량과 동일해야 합니다."
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
            BigDecimal pricePerUnit,
            Long totalQuantity,
            Long minSubscriptionQuantity,
            Long maxSubscriptionQuantity,
            Instant startAt,
            Instant endAt
    ) {
        if (assetId == null) {
            throw new IllegalArgumentException("assetId는 필수입니다.");
        }

        if (issuerId == null) {
            throw new IllegalArgumentException("issuerId는 필수입니다.");
        }

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("공모 상품명은 필수입니다.");
        }

        if (title.length() > 200) {
            throw new IllegalArgumentException("공모 상품명은 200자를 초과할 수 없습니다.");
        }

        if (pricePerUnit == null || pricePerUnit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("단위당 청약 가격은 0보다 커야 합니다.");
        }

        if (totalQuantity == null || totalQuantity <= 0) {
            throw new IllegalArgumentException("총 모집 수량은 0보다 커야 합니다.");
        }

        if (minSubscriptionQuantity == null || minSubscriptionQuantity < 1) {
            throw new IllegalArgumentException("최소 청약 수량은 1 이상이어야 합니다.");
        }

        if (maxSubscriptionQuantity == null
                || maxSubscriptionQuantity < minSubscriptionQuantity) {
            throw new IllegalArgumentException(
                    "최대 청약 수량은 최소 청약 수량 이상이어야 합니다."
            );
        }

        if (maxSubscriptionQuantity > totalQuantity) {
            throw new IllegalArgumentException(
                    "최대 청약 수량은 총 모집 수량을 초과할 수 없습니다."
            );
        }

        if (startAt == null || endAt == null) {
            throw new IllegalArgumentException("모집 시작일과 종료일은 필수입니다.");
        }

        if (!startAt.isBefore(endAt)) {
            throw new IllegalArgumentException(
                    "모집 시작일은 종료일보다 이전이어야 합니다."
            );
        }
    }
}