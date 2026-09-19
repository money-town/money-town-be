package com.moneykk.moneytown.offering.offering.query.repository;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.query.dto.request.OfferingSearchCondition;
import com.moneykk.moneytown.offering.offering.query.dto.response.AiPortfolioCandidateResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
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

    /**
     * AI 포트폴리오 생성에 사용할 공모 후보를 조회한다.
     *
     * 페이징 및 전체 건수 조회 없이 지정된 개수만 반환한다.
     */
    List<AiPortfolioCandidateResponse> findAiPortfolioCandidates(
            Instant now,
            int limit
    );
}