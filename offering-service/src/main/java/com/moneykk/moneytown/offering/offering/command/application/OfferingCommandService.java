package com.moneykk.moneytown.offering.offering.command.application;

import com.moneykk.moneytown.offering.offering.command.dto.request.OfferingCreateRequest;
import com.moneykk.moneytown.offering.offering.command.dto.request.OfferingRejectionRequest;
import com.moneykk.moneytown.offering.offering.command.dto.request.OfferingUpdateRequest;
import com.moneykk.moneytown.offering.offering.command.dto.response.*;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OfferingCommandService {

    private final OfferingRepository offeringRepository;

    @Transactional
    public OfferingCreateResponse create(
            UUID issuerId,
            OfferingCreateRequest request
    ) {
        // TODO: User Service 연동 정책 확정 후 ACTIVE 사용자 검증 여부 결정

        // TODO: Asset Service OpenFeign 연동 후 자산 존재 여부 검증
        // TODO: Asset 상태가 APPROVED인지 검증
        // TODO: 삭제된 Asset인지 검증
        // TODO: Asset 소유자와 issuerId 일치 여부 검증

        Offering offering = Offering.create(
                request.assetId(),
                issuerId,
                request.title(),
                request.pricePerUnit(),
                request.totalQuantity(),
                request.minSubscriptionQuantity(),
                request.maxSubscriptionQuantity(),
                request.startAt(),
                request.endAt()
        );

        Offering savedOffering = offeringRepository.save(offering);

        return OfferingCreateResponse.from(savedOffering);
    }


    @Transactional
    public OfferingReviewRequestResponse requestReview(
            UUID offeringId,
            UUID issuerId
    ) {
        Offering offering = offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId)
                .orElseThrow(() ->
                        new IllegalArgumentException("공모를 찾을 수 없습니다.")
                );

        // TODO: OfferingException / OfferingErrorCode 적용 후 O002로 교체
        if (!offering.getIssuerId().equals(issuerId)) {
            throw new IllegalArgumentException(
                    "해당 공모에 대한 권한이 없습니다."
            );
        }

        offering.requestReview();

        return OfferingReviewRequestResponse.from(offering);
    }

    @Transactional
    public OfferingApprovalResponse approveOffering(
            UUID offeringId,
            UUID reviewerId
    ) {
        Offering offering = offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "공모를 찾을 수 없습니다."
                        )
                );

        // TODO: Gateway/서비스 인가 정책 확정 후 ADMIN 권한 검증 적용
        // TODO: OfferingException / OfferingErrorCode 적용 후
        // O003(공모 없음), O005(승인 권한 없음), O006(승인 불가 상태) 적용

        offering.approve(reviewerId);

        return OfferingApprovalResponse.from(offering);
    }

    @Transactional
    public OfferingRejectionResponse rejectOffering(
            UUID offeringId,
            UUID reviewerId,
            OfferingRejectionRequest request
    ) {
        Offering offering = offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "공모를 찾을 수 없습니다."
                        )
                );

        // TODO: Gateway/서비스 인가 정책 확정 후 ADMIN 권한 검증 적용
        // TODO: OfferingException / OfferingErrorCode 적용 후
        // O003(공모 없음), O005(심사 권한 없음),
        // O007(반려 사유 오류), O008(반려 불가 상태) 적용

        offering.reject(
                reviewerId,
                request.rejectionReason()
        );

        return OfferingRejectionResponse.from(offering);
    }

    @Transactional
    public OfferingUpdateResponse updateOffering(
            UUID offeringId,
            UUID userId,
            String role,
            OfferingUpdateRequest request
    ) {
        Offering offering = offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "공모를 찾을 수 없습니다."
                        )
                );

        boolean owner = offering.getIssuerId().equals(userId);
        boolean admin = "ADMIN".equalsIgnoreCase(role);

        // TODO: Gateway/서비스 인가 정책 확정 후 권한 검증 방식 재검토
        // TODO: OfferingException / OfferingErrorCode 적용 후 O002로 교체
        if (!owner && !admin) {
            throw new IllegalArgumentException(
                    "해당 공모를 수정할 권한이 없습니다."
            );
        }

        offering.update(
                request.title(),
                request.pricePerUnit(),
                request.totalQuantity(),
                request.minSubscriptionQuantity(),
                request.maxSubscriptionQuantity(),
                request.startAt(),
                request.endAt()
        );

        return OfferingUpdateResponse.from(offering);
    }

    @Transactional
    public OfferingDeleteResponse deleteOffering(
            UUID offeringId,
            UUID userId,
            String role
    ) {
        Offering offering = offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "공모를 찾을 수 없습니다."
                        )
                );

        boolean owner = offering.getIssuerId().equals(userId);
        boolean admin = "ADMIN".equalsIgnoreCase(role);

        // TODO: Gateway/서비스 인가 정책 확정 후 권한 검증 방식 재검토
        // TODO: OfferingException / OfferingErrorCode 적용 후 O002로 교체
        if (!owner && !admin) {
            throw new IllegalArgumentException(
                    "해당 공모를 삭제할 권한이 없습니다."
            );
        }

        // TODO: Subscription Service 연동 계약 확정 후 청약 이력 존재 여부 검증
        // 청약 이력이 존재하는 경우 O018
        // "청약 이력이 존재하는 공모는 삭제할 수 없습니다."

        offering.delete(userId);

        return OfferingDeleteResponse.from(offering);
    }
}