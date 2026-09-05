package com.moneykk.moneytown.offering.offering.query.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.offering.query.dto.request.OfferingSearchCondition;
import com.moneykk.moneytown.offering.offering.query.dto.response.OfferingDetailResponse;
import com.moneykk.moneytown.offering.offering.query.dto.response.OfferingListItemResponse;
import com.moneykk.moneytown.offering.offering.query.repository.OfferingQueryRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
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
    private final SubscriptionRepository subscriptionRepository;

    /**
     * 공개 공모 목록을 검색한다.
     */
    public PageResponse<OfferingListItemResponse> searchPublicOfferings(
            OfferingSearchCondition condition,
            Pageable pageable
    ) {
        // 공개 목록 조회에서 비공개 상태가 검색 조건으로 전달되는 것을 차단한다.
        validatePublicSearchCondition(condition);
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
     *
     * 공개 상태의 공모는 누구나 조회할 수 있다.
     * 비공개 상태의 공모는 소유 ISSUER 또는 ADMIN만 조회할 수 있다.
     *
     */
    public OfferingDetailResponse getOffering(
            UUID offeringId,
            UUID userId,
            String role
    ) {
        Offering offering = offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId)
                .orElseThrow(() ->
                        new BusinessException(
                                OfferingErrorCode.OFFERING_NOT_FOUND
                        )
                );

        OfferingStatus status = offering.getOfferingStatus();

        // 1. 공개 상태
        if (isPublicStatus(status)) {
            boolean includePrivateFields =
                    isOwner(offering, userId) || isAdmin(role);

            return OfferingDetailResponse.from(
                    offering,
                    includePrivateFields
            );
        }

        // 2. CANCELLED 별도 정책
        if (status == OfferingStatus.CANCELLED) {
            return getCancelledOfferingDetail(
                    offering,
                    userId,
                    role
            );
        }

        // 3. 일반 비공개 상태 - DRAFT / REVIEW_REQUESTED / REJECTED / CANCELLING
        if (!isOwner(offering, userId) && !isAdmin(role)) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_ACCESS_DENIED
            );
        }

        return OfferingDetailResponse.from(offering, true);
    }


    /**
     * CANCELLED 공모의 상세 조회 권한을 검증한다.
     *
     * - 소유 ISSUER / ADMIN: 조회 가능 + 관리용 private field 포함
     * - INVESTOR: 공모 취소 보상 완료로 자신의 청약이 CANCELLED된 경우만 해당 공모 상세 조회 가능
     * - 관리용 private field는 제외
     * - 그 외 사용자: 조회 불가
     *
     * Saga 보상 흐름 구현 후 Subscription이
     * COMPENSATING -> CANCELLED로 정상 전환되면 해당 상태를 기준으로 관련 투자자를 판별한다.
     */
    private OfferingDetailResponse getCancelledOfferingDetail(
            Offering offering,
            UUID userId,
            String role
    ) {
        if (isOwner(offering, userId) || isAdmin(role)) {
            return OfferingDetailResponse.from(
                    offering,
                    true
            );
        }

        if (isCompensatedInvestor(
                offering.getOfferingId(),
                userId,
                role
        )) {
            return OfferingDetailResponse.from(
                    offering,
                    false
            );
        }

        throw new BusinessException(
                OfferingErrorCode.OFFERING_ACCESS_DENIED
        );
    }

    /**
     * 공모 취소로 인해 실제 보상 완료된 투자자인지 확인한다.
     */
    private boolean isCompensatedInvestor(
            UUID offeringId,
            UUID userId,
            String role
    ) {
        if (userId == null
                || role == null
                || !"INVESTOR".equalsIgnoreCase(role)) {
            return false;
        }

        return subscriptionRepository
                .existsByOfferingIdAndUserIdAndSubscriptionStatusAndCancellationTypeIsNotNullAndIsDeletedFalse(
                        offeringId,
                        userId,
                        SubscriptionStatus.CANCELLED
                );
    }

    /**
     * 공개 목록 조회에 사용할 수 있는 공모 상태인지 검증한다.
     *
     * SCHEDULED, OPEN, SOLD_OUT, CLOSED 상태만
     * 공개 목록의 검색 조건으로 허용한다.
     */
    private void validatePublicSearchCondition(
            OfferingSearchCondition condition
    ) {
        if (condition.offeringStatus() != null
                && !isPublicStatus(condition.offeringStatus())) {
            throw new BusinessException(
                    OfferingErrorCode.INVALID_OFFERING_SEARCH_CONDITION
            );
        }
    }

    /**
     * 일반 사용자에게 공개 가능한 공모 상태인지 확인한다.
     */
    private boolean isPublicStatus(OfferingStatus status) {
        return switch (status) {
            case SCHEDULED, OPEN, SOLD_OUT, CLOSED -> true;
            case DRAFT, REVIEW_REQUESTED, REJECTED,
                 CANCELLING, CANCELLED -> false;
        };
    }

    /**
     * 현재 사용자가 해당 공모의 소유자인지 확인한다.
     */
    private boolean isOwner(Offering offering, UUID userId) {
        return userId != null
                && offering.getIssuerId().equals(userId);
    }

    /**
     * 현재 사용자가 관리자인지 확인한다.
     */
    private boolean isAdmin(String role) {
        return role != null
                && "ADMIN".equalsIgnoreCase(role);

    }
}