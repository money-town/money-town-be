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
import com.moneykk.moneytown.offering.subscription.infrastructure.client.UserServiceClient;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.UserInvestmentEligibilityResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OfferingCommandService {

    private static final int MAX_OFFERING_TITLE_LENGTH = 200;

    private final OfferingRepository offeringRepository;
    private final AssetServiceClient assetServiceClient;
    private final UserServiceClient userServiceClient;

    private final OfferingTransactionService offeringTransactionService;

    public OfferingCreateResponse create(
            UUID issuerId,
            OfferingCreateRequest request
    ) {
        validateIssuerEligibility(issuerId);

        // availableShareQuantity 기준 검증
        AssetOfferingInfoResponse asset =
                getValidatedAssetForOffering(
                        request.assetId(),
                        issuerId,
                        request.totalQuantity()
                );

        String title = createOfferingTitle(asset);

        return offeringTransactionService.createOffering(
                issuerId,
                request,
                title,
                asset.unitPrice()
        );
    }


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

        validateIssuerEligibility(issuerId);

        // 심사 요청 시 자산의 현재 상태와 소유권을 다시 검증한다.
        validateAssetForReview(
                offering.getAssetId(),
                issuerId
        );

        return offeringTransactionService.requestReview(
                offeringId,
                issuerId
        );
    }

    @Transactional
    public OfferingApprovalResponse approveOffering(
            UUID offeringId,
            UUID reviewerId
    ) {
        Offering offering = findOfferingForUpdate(offeringId);

        offering.approve(reviewerId);

        return OfferingApprovalResponse.from(offering);
    }

    @Transactional
    public OfferingRejectionResponse rejectOffering(
            UUID offeringId,
            UUID reviewerId,
            OfferingRejectionRequest request
    ) {
        Offering offering = findOfferingForUpdate(offeringId);

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

        if ("ISSUER".equalsIgnoreCase(role)) {
            validateIssuerEligibility(userId);
        }

        Long targetTotalQuantity =
                request.totalQuantity() != null
                        ? request.totalQuantity()
                        : offering.getTotalQuantity();

        // 공모 수정은 DRAFT 상태에서만 허용되므로,
        // 현재 공모에서 이미 배정된 청약 수량은 존재하지 않는다.
        // 따라서 등록과 동일하게 Asset의 현재 가용 수량을 기준으로 검증한다.
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

        if ("ISSUER".equalsIgnoreCase(role)) {
            validateIssuerEligibility(userId);
        }

        return offeringTransactionService.deleteOffering(
                offeringId,
                userId,
                role
        );
    }

    /**
     * User Service에서 최신 사용자 상태를 조회하여
     * 공모 생성·수정·삭제 및 심사 요청 자격을 검증한다.
     *
     * ISSUER 역할이고 계정과 KYC가 유효한 경우에만 허용한다.
     */
    private void validateIssuerEligibility(UUID issuerId) {
        try {
            ApiResponse<UserInvestmentEligibilityResponse> response =
                    userServiceClient.getInvestmentEligibility(issuerId);

            UserInvestmentEligibilityResponse user =
                    response != null
                            ? response.data()
                            : null;

            validateUserResponse(
                    issuerId,
                    user
            );

            if (!user.isEligibleForOfferingManagement(Instant.now())) {
                throw new BusinessException(
                        OfferingErrorCode.OFFERING_ISSUER_ELIGIBILITY_NOT_MET
                );
            }

        } catch (FeignException.NotFound e) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_USER_NOT_FOUND
            );

        } catch (FeignException e) {
            throw new BusinessException(
                    OfferingErrorCode.USER_SERVICE_UNAVAILABLE
            );
        }
    }

    private void validateUserResponse(
            UUID requestedUserId,
            UserInvestmentEligibilityResponse user
    ) {
        if (user == null
                || user.userId() == null
                || !requestedUserId.equals(user.userId())
                || user.userRole() == null
                || user.accountStatus() == null
                || user.kycStatus() == null
                || user.kycExpiresAt() == null) {
            throw new BusinessException(
                    OfferingErrorCode.USER_RESPONSE_INVALID
            );
        }
    }


    private void validateAssetForReview(
            UUID assetId,
            UUID issuerId
    ) {
        ApiResponse<AssetOfferingInfoResponse> assetResponse =
                getAsset(assetId);

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
                getAsset(assetId);

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

    private String createOfferingTitle(
            AssetOfferingInfoResponse asset
    ) {
        // 공모 생성에서 실제 필요한 자산명만 검증
        if (asset.assetName() == null
                || asset.assetName().isBlank()) {
            throw new BusinessException(
                    OfferingErrorCode.ASSET_RESPONSE_INVALID
            );
        }

        String suffix = " 공모";
        String assetName = asset.assetName().trim();

        int maxAssetNameLength =
                MAX_OFFERING_TITLE_LENGTH - suffix.length();

        // 자산 원본 이름은 그대로 두고 공모 제목에서만 길이를 조정
        String titleAssetName =
                assetName.length() > maxAssetNameLength
                        ? assetName.substring(0, maxAssetNameLength)
                        : assetName;

        return titleAssetName + suffix;
    }


    private ApiResponse<AssetOfferingInfoResponse> getAsset(
            UUID assetId
    ) {
        try {
            return assetServiceClient.getAsset("SYSTEM", assetId);

        } catch (FeignException.NotFound e) {
            throw new BusinessException(
                    OfferingErrorCode.OFFERING_ASSET_NOT_FOUND
            );

        } catch (FeignException e) {
            throw new BusinessException(
                    OfferingErrorCode.ASSET_SERVICE_UNAVAILABLE
            );
        }
    }

    private void validateOwnerOrAdmin(
            Offering offering,
            UUID userId,
            String role
    ) {
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
    }

    private Offering findOfferingForUpdate(UUID offeringId) {
        return offeringRepository
                .findByIdForUpdate(offeringId)
                .orElseThrow(() -> new BusinessException(
                        OfferingErrorCode.OFFERING_NOT_FOUND
                ));
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