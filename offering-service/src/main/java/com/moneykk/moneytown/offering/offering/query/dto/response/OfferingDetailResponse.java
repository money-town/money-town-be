package com.moneykk.moneytown.offering.offering.query.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moneykk.moneytown.offering.offering.domain.entity.CancellationType;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfferingDetailResponse(
        UUID offeringId,
        UUID assetId,
        UUID issuerId,
        String title,
        Long pricePerUnit,
        Long totalQuantity,
        Long remainingQuantity,
        Long minSubscriptionQuantity,
        Long maxSubscriptionQuantity,
        Instant startAt,
        Instant endAt,
        OfferingStatus offeringStatus,
        CancellationType cancellationType,
        Instant createdAt,
        Instant updatedAt
) {

    // TODO: 응답 조합 로직이 복잡해질 경우 Builder 패턴 적용 검토
    public static OfferingDetailResponse from(
            Offering offering,
            boolean includePrivateFields
    ) {
        return new OfferingDetailResponse(
                offering.getOfferingId(),
                offering.getAssetId(),
                includePrivateFields ? offering.getIssuerId() : null,
                offering.getTitle(),
                offering.getPricePerUnit(),
                offering.getTotalQuantity(),
                offering.getRemainingQuantity(),
                offering.getMinSubscriptionQuantity(),
                offering.getMaxSubscriptionQuantity(),
                offering.getStartAt(),
                offering.getEndAt(),
                offering.getOfferingStatus(),
                includePrivateFields ? offering.getCancellationType() : null,
                offering.getCreatedAt(),
                offering.getUpdatedAt()
        );
    }
}