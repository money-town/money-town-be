package com.moneykk.moneytown.offering.offering.command.application;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.subscription.domain.entity.CancellationType;

import java.util.Objects;

final class OfferingCancellationTypeMapper {

    private OfferingCancellationTypeMapper() {
    }

    static CancellationType toSubscriptionType(
            Offering offering
    ) {
        Objects.requireNonNull(
                offering,
                "offering은 필수입니다."
        );

        if (offering.getCancellationType() == null) {
            throw new IllegalStateException(
                    "취소 처리 중인 공모의 cancellationType이 없습니다. "
                            + "offeringId=" + offering.getOfferingId()
            );
        }

        return switch (offering.getCancellationType()) {
            case ADMIN_CANCELLED ->
                    CancellationType.OFFERING_ADMIN_CANCELLED;
            case UNDER_SUBSCRIBED ->
                    CancellationType.OFFERING_UNDER_SUBSCRIBED;
        };
    }
}
