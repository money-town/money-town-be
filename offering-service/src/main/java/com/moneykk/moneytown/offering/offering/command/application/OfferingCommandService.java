package com.moneykk.moneytown.offering.offering.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.offering.command.dto.request.OfferingCreateRequest;
import com.moneykk.moneytown.offering.offering.command.dto.request.OfferingRejectionRequest;
import com.moneykk.moneytown.offering.offering.command.dto.request.OfferingUpdateRequest;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingApprovalResponse;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingCreateResponse;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingDeleteResponse;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingRejectionResponse;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingReviewRequestResponse;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingUpdateResponse;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.offering.infrastructure.client.AssetServiceClient;
import com.moneykk.moneytown.offering.offering.infrastructure.client.dto.AssetOfferingInfoResponse;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OfferingCommandService {

    private final OfferingRepository offeringRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final AssetServiceClient assetServiceClient;

    private final OfferingTransactionService offeringTransactionService;

    public OfferingCreateResponse create(
            UUID issuerId,
            OfferingCreateRequest request
    ) {
        // TODO: User Service 연동 정책 확정 후 ACTIVE 사용자 검증 여부 결정

        AssetOfferingInfoResponse asset =
                getValidatedAssetForOffering(
                        request.assetId(),
                        issuerId,
                        request.totalQuantity()
                );

        return offeringTransactionService.createOffering(
                issuerId,
                request,
                asset.unitPrice()
        );
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

    @Transactional
    public OfferingApprovalResponse approveOffering(
            UUID offeringId,
            UUID reviewerId
    ) {
        Offering offering = findOffering(offeringId);

        offering.approve(reviewerId);

        return OfferingApprovalResponse.from(offering);
    }

    @Transactional
    public OfferingRejectionResponse rejectOffering(
            UUID offeringId,
            UUID reviewerId,
            OfferingRejectionRequest request
    ) {
        Offering offering = findOffering(offeringId);

        offering.reject(
                reviewerId,
                request.rejectionReason()
        );

        return OfferingRejectionResponse.from(offering);
    }

    public OfferingUpdateResponse updateOffering(
            UUID offeringId,
            UUID userId,
            String role,
            OfferingUpdateRequest request
    ) {
        Offering offering = findOffering(offeringId);

        validateOwnerOrAdmin(
                offering,
                userId,
                role
        );

        Long targetTotalQuantity =
                request.totalQuantity() != null
                        ? request.totalQuantity()
                        : offering.getTotalQuantity();

        // TODO: Asset.allocatedQuantity에 현재 공모를 통해 이미 배정된 수량이 포함되는지 확인 필요.
        // 포함된다면 공모 수정 시 availableShareQuantity 계산 정책을 별도로 적용해야 한다.
        getValidatedAssetForOffering(
                offering.getAssetId(),
                offering.getIssuerId(),
                targetTotalQuantity
        );

        return offeringTransactionService.updateOffering(
                offeringId,
                userId,
                role,
                request
        );
    }

    @Transactional
    public OfferingDeleteResponse deleteOffering(
            UUID offeringId,
            UUID userId,
            String role
    ) {
        Offering offering = findOffering(offeringId);

        validateOwnerOrAdmin(
                offering,
                userId,
                role
        );

        // 청약 이력이 존재하면 공모 삭제를 허용하지 않는다.
        boolean hasSubscriptions =
                subscriptionRepository.existsByOfferingId(offeringId);

        if (hasSubscriptions) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_HAS_SUBSCRIPTIONS
            );
        }

        offering.delete(userId);

        return OfferingDeleteResponse.from(offering);
    }

    private AssetOfferingInfoResponse getValidatedAssetForOffering(
            UUID assetId,
            UUID issuerId,
            Long totalQuantity
    ) {
        if (totalQuantity == null || totalQuantity <= 0) {
            throw new BusinessException(
                    OfferingErrorCode.INVALID_OFFERING_QUANTITY
            );
        }

        ApiResponse<AssetOfferingInfoResponse> assetResponse =
                assetServiceClient.getAsset(assetId);

        AssetOfferingInfoResponse asset =
                assetResponse != null
                        ? assetResponse.data()
                        : null;

        validateAssetResponse(
                assetId,
                asset
        );

        if (!"APPROVED".equalsIgnoreCase(asset.assetStatus())) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_ASSET_NOT_AVAILABLE
            );
        }

        if (!issuerId.equals(asset.userId())) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_ASSET_ACCESS_DENIED
            );
        }

        if (asset.allocatedQuantity() > asset.totalShareQuantity()) {
            throw new BusinessException(
                    OfferingErrorCode.ASSET_QUANTITY_STATE_INVALID
            );
        }

        long availableShareQuantity =
                asset.totalShareQuantity()
                        - asset.allocatedQuantity();

        if (totalQuantity > availableShareQuantity) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_QUANTITY_EXCEEDS_AVAILABLE
            );
        }

        return asset;
    }

    /**
     * Asset Service에서 전달받은 자산 정보의 유효성을 검증한다.
     *
     * totalShareQuantity
     * - 자산의 전체 발행 지분 수량
     * - 공모에 사용하려면 반드시 1 이상이어야 한다.
     *
     * allocatedQuantity
     * - 이미 투자자에게 배정 완료된 지분 수량
     * - 아직 배정된 지분이 없다면 0이 정상이다.
     */
    private void validateAssetResponse(
            UUID requestedAssetId,
            AssetOfferingInfoResponse asset
    ) {
        if (asset == null
                || asset.assetId() == null
                || !requestedAssetId.equals(asset.assetId())
                || asset.userId() == null
                || asset.unitPrice() == null
                || asset.unitPrice() <= 0
                || asset.totalShareQuantity() == null
                || asset.totalShareQuantity() <= 0
                || asset.allocatedQuantity() == null
                || asset.allocatedQuantity() < 0
                || asset.assetStatus() == null) {
            throw new BusinessException(
                    OfferingErrorCode.ASSET_RESPONSE_INVALID
            );
        }
    }

    private void validateOwnerOrAdmin(
            Offering offering,
            UUID userId,
            String role
    ) {
        boolean owner =
                offering.getIssuerId().equals(userId);

        boolean admin =
                "ADMIN".equalsIgnoreCase(role);

        if (!owner && !admin) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_ACCESS_DENIED
            );
        }
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