package com.moneykk.moneytown.offering.offering.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.offering.command.dto.request.OfferingCreateRequest;
import com.moneykk.moneytown.offering.offering.command.dto.request.OfferingUpdateRequest;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingCreateResponse;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingReviewRequestResponse;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingUpdateResponse;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OfferingTransactionService {

    private final OfferingRepository offeringRepository;

    @Transactional
    public OfferingCreateResponse createOffering(
            UUID issuerId,
            OfferingCreateRequest request,
            Long unitPrice
    ) {
        Offering offering = Offering.create(
                request.assetId(),
                issuerId,
                request.title(),
                unitPrice,
                request.totalQuantity(),
                request.minSubscriptionQuantity(),
                request.maxSubscriptionQuantity(),
                request.startAt(),
                request.endAt()
        );

        Offering savedOffering =
                offeringRepository.save(offering);

        return OfferingCreateResponse.from(savedOffering);
    }

    @Transactional
    public OfferingUpdateResponse updateOffering(
            UUID offeringId,
            UUID userId,
            String role,
            OfferingUpdateRequest request
    ) {
        Offering offering = findOffering(offeringId);

        boolean owner =
                offering.getIssuerId().equals(userId);

        boolean admin =
                "ADMIN".equalsIgnoreCase(role);

        if (!owner && !admin) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_ACCESS_DENIED
            );
        }

        offering.update(
                request.title(),
                request.totalQuantity(),
                request.minSubscriptionQuantity(),
                request.maxSubscriptionQuantity(),
                request.startAt(),
                request.endAt()
        );

        return OfferingUpdateResponse.from(offering);
    }

    @Transactional
    public OfferingReviewRequestResponse requestReview(
            UUID offeringId,
            UUID issuerId
    ) {
        Offering offering = findOffering(offeringId);

        if (!offering.getIssuerId().equals(issuerId)) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_ACCESS_DENIED
            );
        }

        offering.requestReview();

        return OfferingReviewRequestResponse.from(offering);
    }

    private Offering findOffering(UUID offeringId) {
        return offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId)
                .orElseThrow(() ->
                        new BusinessException(
                                OfferingErrorCode.OFFERING_NOT_FOUND
                        )
                );
    }
}
