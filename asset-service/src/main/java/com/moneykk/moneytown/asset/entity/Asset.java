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

    // 자산 소유주 이름
    @Column(name = "owner_name", length = 200)
    private String ownerName;

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

    // 단가 절사로 발생한 차액(원). 소유주 채무가 아님
    @Column(name = "rounding_difference_amount", nullable = false)
    private long roundingDifferenceAmount;

    public Asset(UUID userId, String assetName, AssetType type, String description,
                 long valuationAmount, BigDecimal expectedReturnRate,
                 Map<String, Object> detailData, long totalShareQuantity) {
        // 평가 금액과 수량은 양수, 지분 단가는 최소 1원
        if (valuationAmount <= 0 || totalShareQuantity <= 0
                || valuationAmount < totalShareQuantity) {
            throw new BusinessException(AssetErrorCode.INVALID_ASSET_SHARE_PRICE);
        }
        this.userId = userId;
        this.assetName = assetName;
        this.type = type;
        this.description = description;
        this.valuationAmount = valuationAmount;
        this.expectedReturnRate = expectedReturnRate;
        this.detailData = new HashMap<>(detailData);
        // 상세 정보에 평가금액이 있으면 기준 평가금액과 같은 값으로 저장
        if (this.detailData.containsKey("appraisalAmount")) {
            this.detailData.put("appraisalAmount", valuationAmount);
        }
        // 지분당 가격은 1원 미만 버림
        this.unitPrice = valuationAmount / totalShareQuantity;
        this.totalShareQuantity = totalShareQuantity;
        // 평가금액과 전체 지분 가격 합계의 차이를 저장
        this.roundingDifferenceAmount = valuationAmount - this.unitPrice * totalShareQuantity;
        this.allocatedQuantity = 0;
        this.status = AssetStatus.DRAFT;
    }

    /**
     * 지분 배정
     */
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

    /**
     * 지분 회수
     */
    public void revokeShares(long quantity) {
        if (quantity <= 0) {
            throw new BusinessException(
                    AssetErrorCode.INVALID_HOLDING_QUANTITY
            );
        }

        if (allocatedQuantity < quantity) {
            throw new BusinessException(
                    AssetErrorCode.INSUFFICIENT_ALLOCATED_QUANTITY
            );
        }

        allocatedQuantity -= quantity;
    }

    /**
     * 자산 정보 부분 수정
     */
    public void updateInfo(
            String name,
            String description,
            String ownerName,
            Map<String, Object> detail,
            Long valuationAmount,
            Long totalShareQuantity
    ) {
        // 작성 중이거나 반려된 자산만 수정 가능
        if (status != AssetStatus.DRAFT
                && status != AssetStatus.REJECTED) {
            throw new BusinessException(
                    AssetErrorCode.ASSET_UPDATE_NOT_ALLOWED
            );
        }

        // 전달하지 않은 값은 기존 값 유지
        long nextValuation = valuationAmount != null
                ? valuationAmount : this.valuationAmount;

        long nextQuantity = totalShareQuantity != null
                ? totalShareQuantity : this.totalShareQuantity;

        // 평가금액과 수량은 양수, 지분당 가격은 최소 1원
        if (nextValuation <= 0
                || nextQuantity <= 0
                || nextValuation < nextQuantity) {
            throw new BusinessException(
                    AssetErrorCode.INVALID_ASSET_SHARE_PRICE
            );
        }

        // 이미 배정된 수량보다 줄일 수 없음
        if (nextQuantity < allocatedQuantity) {
            throw new BusinessException(
                    AssetErrorCode.INVALID_HOLDING_QUANTITY
            );
        }

        // 상세 정보는 전달된 항목만 변경
        Map<String, Object> nextDetail = new HashMap<>(this.detailData);

        if (detail != null) {
            nextDetail.putAll(detail);
        }

        // 상세 평가금액과 실제 계산 금액을 일치시킴
        if (valuationAmount != null) {
            nextDetail.put("appraisalAmount", nextValuation);
        }

        // 일반 정보 변경
        if (name != null) {
            this.assetName = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (ownerName != null) {
            this.ownerName = ownerName;
        }

        this.detailData = nextDetail;
        this.valuationAmount = nextValuation;
        this.totalShareQuantity = nextQuantity;

        // 1원 미만 버림
        this.unitPrice = nextValuation / nextQuantity;

        // 절사로 발생한 차액 저장
        this.roundingDifferenceAmount =
                nextValuation - (this.unitPrice * nextQuantity);
    }

    /**
     * 자산 상태 변경
     */
    public void changeStatus(
            AssetStatus nextStatus,
            String rejectionReason
    ) {
        // 허용된 상태 변경인지 확인
        boolean allowed = switch (this.status) {
            case DRAFT, REJECTED -> nextStatus == AssetStatus.REVIEW_REQUESTED;

            case REVIEW_REQUESTED -> nextStatus == AssetStatus.APPROVED
                    || nextStatus == AssetStatus.REJECTED;

            case APPROVED -> nextStatus == AssetStatus.SUSPENDED;

            case SUSPENDED -> nextStatus == AssetStatus.APPROVED;

            case TERMINATED -> false;
        };

        if (!allowed) {
            throw new BusinessException(
                    AssetErrorCode.INVALID_ASSET_STATUS_TRANSITION
            );
        }

        // 반려 상태는 반려 사유 필수
        if (nextStatus == AssetStatus.REJECTED
                && (rejectionReason == null || rejectionReason.isBlank())) {
            throw new BusinessException(
                    AssetErrorCode.ASSET_REJECTION_REASON_REQUIRED
            );
        }

        this.status = nextStatus;

        // 반려 상태가 아니면 이전 반려 사유 제거
        this.rejectionReason = nextStatus == AssetStatus.REJECTED
                ? rejectionReason.trim()
                : null;
    }

    /**
     * 자산 소프트 삭제
     */
    public void delete(UUID deletedBy) {
        // 작성 중이거나 반려된 자산만 삭제 가능
        if (status != AssetStatus.DRAFT
                && status != AssetStatus.REJECTED) {
            throw new BusinessException(
                    AssetErrorCode.ASSET_DELETE_NOT_ALLOWED
            );
        }

        // 삭제 시간과 삭제 사용자 기록
        softDelete(deletedBy);
    }

    /**
     * 대표 이미지 등록 및 변경
     */
    public void updateRepresentativeImage(
            String representativeImageKey
    ) {
        if (representativeImageKey == null
                || representativeImageKey.isBlank()) {
            throw new BusinessException(
                    AssetErrorCode.INVALID_ASSET_IMAGE
            );
        }

        this.representativeImageKey =
                representativeImageKey;
    }
}
