package com.moneykk.moneytown.offering.offering.query.application;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.offering.query.dto.response.OfferingDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OfferingQueryService {

    private final OfferingRepository offeringRepository;

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
            case SCHEDULED, OPEN, SOLD_OUT, CLOSED, CANCELLED -> true;
            case DRAFT, REVIEW_REQUESTED, REJECTED, CANCELLING -> false;
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