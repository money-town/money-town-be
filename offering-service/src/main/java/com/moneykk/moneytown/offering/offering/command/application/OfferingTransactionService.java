package com.moneykk.moneytown.offering.offering.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.offering.command.dto.request.OfferingCreateRequest;
import com.moneykk.moneytown.offering.offering.command.dto.request.OfferingUpdateRequest;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingCreateResponse;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingDeleteResponse;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingReviewRequestResponse;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingUpdateResponse;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OfferingTransactionService {

    private final OfferingRepository offeringRepository;
    private final SubscriptionRepository subscriptionRepository;

    private static final ZoneId SERVICE_ZONE_ID = ZoneId.of("Asia/Seoul");

    @Transactional
    public OfferingCreateResponse createOffering(
            UUID issuerId,
            OfferingCreateRequest request,
            String title,
            Long unitPrice
    ) {
        Offering offering = Offering.create(
                request.assetId(),
                issuerId,
                title,
                unitPrice,
                request.totalQuantity(),
                request.minSubscriptionQuantity(),
                request.maxSubscriptionQuantity(),
                toInstant(request.startAt()),
                toInstant(request.endAt())
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
        Offering offering = findOfferingForUpdate(offeringId);

        boolean ownerIssuer =
                "ISSUER".equalsIgnoreCase(role)
                        && offering.getIssuerId().equals(userId);

        boolean admin =
                "ADMIN".equalsIgnoreCase(role);

        if (!ownerIssuer && !admin) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_ACCESS_DENIED
            );
        }

        offering.update(
                request.title(),
                request.totalQuantity(),
                request.minSubscriptionQuantity(),
                request.maxSubscriptionQuantity(),
                toInstant(request.startAt()),
                toInstant(request.endAt())
        );

        return OfferingUpdateResponse.from(offering);
    }

    private Instant toInstant(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }

        return dateTime
                .atZone(SERVICE_ZONE_ID)
                .toInstant();
    }

    @Transactional
    public OfferingDeleteResponse deleteOffering(
            UUID offeringId,
            UUID userId,
            String role
    ) {
        Offering offering = findOfferingForUpdate(offeringId);

        boolean ownerIssuer =
                "ISSUER".equalsIgnoreCase(role)
                        && offering.getIssuerId().equals(userId);

        boolean admin =
                "ADMIN".equalsIgnoreCase(role);

        if (!ownerIssuer && !admin) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_ACCESS_DENIED
            );
        }

        if (subscriptionRepository.existsByOfferingId(offeringId)) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_HAS_SUBSCRIPTIONS
            );
        }

        offering.delete(userId);

        return OfferingDeleteResponse.from(offering);
    }

    @Transactional
    public OfferingReviewRequestResponse requestReview(
            UUID offeringId,
            UUID issuerId
    ) {
        Offering offering = findOfferingForUpdate(offeringId);

        if (!offering.getIssuerId().equals(issuerId)) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_ACCESS_DENIED
            );
        }

        offering.requestReview();

        return OfferingReviewRequestResponse.from(offering);
    }

    private Offering findOfferingForUpdate(UUID offeringId) {
        return offeringRepository
                .findByIdForUpdate(offeringId)
                .orElseThrow(() -> new BusinessException(
                        OfferingErrorCode.OFFERING_NOT_FOUND
                ));
    }
}
