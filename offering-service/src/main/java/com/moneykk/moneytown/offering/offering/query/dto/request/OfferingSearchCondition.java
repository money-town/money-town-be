package com.moneykk.moneytown.offering.offering.query.dto.request;

import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;

public record OfferingSearchCondition(
        OfferingStatus offeringStatus,
        String keyword
) {
}