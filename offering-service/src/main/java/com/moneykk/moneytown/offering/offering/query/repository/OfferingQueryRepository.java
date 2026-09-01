package com.moneykk.moneytown.offering.offering.query.repository;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.query.dto.request.OfferingSearchCondition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OfferingQueryRepository {

    Page<Offering> searchPublicOfferings(
            OfferingSearchCondition condition,
            Pageable pageable
    );

    Page<Offering> searchMyOfferings(
            UUID issuerId,
            OfferingSearchCondition condition,
            Pageable pageable
    );

    Page<Offering> searchOfferingsForManagement(
            OfferingSearchCondition condition,
            Pageable pageable
    );
}