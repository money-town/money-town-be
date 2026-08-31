package com.moneykk.moneytown.offering.offering.query.application;

import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.offering.query.dto.request.OfferingSearchCondition;
import com.moneykk.moneytown.offering.offering.query.dto.response.OfferingDetailResponse;
import com.moneykk.moneytown.offering.offering.query.dto.response.OfferingListItemResponse;
import com.moneykk.moneytown.offering.offering.query.repository.OfferingQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OfferingQueryService {

    private final OfferingRepository offeringRepository;
    private final OfferingQueryRepository offeringQueryRepository;

    /**
     * 공개 공모 목록을 검색한다.
     */
    public PageResponse<OfferingListItemResponse> searchPublicOfferings(
            OfferingSearchCondition condition,
            Pageable pageable
    ) {
        Page<Offering> offerings =
                offeringQueryRepository.searchPublicOfferings(
                        condition,
                        pageable
                );

        return PageResponse.from(
                offerings,
                OfferingListItemResponse::from
        );
    }

    /**
     * 현재 로그인한 발행자의 공모 목록을 조회한다.
     */
    public PageResponse<OfferingListItemResponse> searchMyOfferings(
            UUID issuerId,
            OfferingSearchCondition condition,
            Pageable pageable
    ) {
        Page<Offering> offerings =
                offeringQueryRepository.searchMyOfferings(
                        issuerId,
                        condition,
                        pageable
                );

        return PageResponse.from(
                offerings,
                OfferingListItemResponse::from
        );
    }

    /**
     * 관리자용 공모 목록을 조회한다.
     */
    public PageResponse<OfferingListItemResponse> searchOfferingsForManagement(
            OfferingSearchCondition condition,
            Pageable pageable
    ) {
        Page<Offering> offerings =
                offeringQueryRepository.searchOfferingsForManagement(
                        condition,
                        pageable
                );

        return PageResponse.from(
                offerings,
                OfferingListItemResponse::from
        );
    }

    /**
     * 공모 상품을 상세 조회한다.
     */
    public OfferingDetailResponse getOffering(
            UUID offeringId,
            UUID userId,
            String role
    ) {
        Offering offering = offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId)
                .orElseThrow(() ->
                        new IllegalArgumentException("공모를 찾을 수 없습니다.")
                );

        if (isPublicStatus(offering.getOfferingStatus())) {
            boolean includePrivateFields =
                    isOwner(offering, userId) || isAdmin(role);

            return OfferingDetailResponse.from(
                    offering,
                    includePrivateFields
            );
        }

        // TODO: Gateway의 실제 Role 전달 계약이 확정되기 전 임시 구현
        // TODO: OfferingException / OfferingErrorCode 적용 후
        // O002, O003으로 교체
        if (!isOwner(offering, userId) && !isAdmin(role)) {
            throw new IllegalArgumentException(
                    "해당 공모를 조회할 권한이 없습니다."
            );
        }

        return OfferingDetailResponse.from(offering, true);
    }

    private boolean isPublicStatus(OfferingStatus status) {
        return switch (status) {
            case SCHEDULED, OPEN, SOLD_OUT, CLOSED -> true;
            case DRAFT, REVIEW_REQUESTED, REJECTED,
                 CANCELLING, CANCELLED -> false;
        };
    }

    private boolean isOwner(Offering offering, UUID userId) {
        return userId != null
                && offering.getIssuerId().equals(userId);
    }

    private boolean isAdmin(String role) {
        return role != null
                && "ADMIN".equalsIgnoreCase(role);
    }
}