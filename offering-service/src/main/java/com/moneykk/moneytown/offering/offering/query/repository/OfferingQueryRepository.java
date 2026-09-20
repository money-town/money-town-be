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

    /**
     * 공개 공모 목록의 내용(페이지 내 항목)만 조회한다.
     *
     * 전체 건수는 {@link #countPublicOfferings}로 별도 조회한다.
     * COUNT와 달리 캐싱하지 않는다 — 항상 최신 목록을 반환해야 하고,
     * LIMIT이 걸려 있어 캐싱 없이도 비용이 낮기 때문이다.
     */
    List<Offering> searchPublicOfferingsContent(
            OfferingSearchCondition condition,
            Pageable pageable
    );

    /**
     * 공개 공모 목록 조건에 매치되는 전체 건수를 조회한다.
     *
     * 조건에 맞는 모든 행을 순회해야 하는 COUNT의 특성상
     * 매치 건수가 클 경우 목록 SELECT보다 비용이 크게 든다.
     * 구현체에서 짧은 TTL로 캐싱해 동시 요청 시 반복 실행을 줄인다.
     */
    long countPublicOfferings(
            OfferingSearchCondition condition
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