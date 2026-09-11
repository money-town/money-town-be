package com.moneykk.moneytown.offering.offering.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.offering.command.dto.request.OfferingCreateRequest;
import com.moneykk.moneytown.offering.offering.command.dto.request.OfferingUpdateRequest;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingCreateResponse;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingUpdateResponse;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingDeleteResponse;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.offering.infrastructure.client.AssetServiceClient;
import com.moneykk.moneytown.offering.offering.infrastructure.client.dto.AssetOfferingInfoResponse;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.UserServiceClient;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.UserInvestmentEligibilityResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferingCommandServiceTest {

    @Mock
    private OfferingRepository offeringRepository;

    @Mock
    private AssetServiceClient assetServiceClient;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private OfferingTransactionService offeringTransactionService;

    @InjectMocks
    private OfferingCommandService offeringCommandService;

    @Test
    @DisplayName("유효한 ISSUER는 사용자와 자산 검증 후 자동 생성한 제목으로 공모를 생성한다")
    void createsOfferingForEligibleIssuer() {
        // given
        UUID issuerId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        OfferingCreateRequest request = createRequest(assetId);

        UserInvestmentEligibilityResponse user =
                eligibleIssuer(issuerId);

        AssetOfferingInfoResponse asset =
                availableAsset(assetId, issuerId);

        OfferingCreateResponse expectedResponse =
                mock(OfferingCreateResponse.class);

        when(userServiceClient.getInvestmentEligibility(issuerId))
                .thenReturn(ApiResponse.success(
                        user,
                        "사용자 조회 성공"
                ));

        when(assetServiceClient.getAsset("SYSTEM", assetId))
                .thenReturn(ApiResponse.success(
                        asset,
                        "자산 조회 성공"
                ));

        when(offeringTransactionService.createOffering(
                issuerId,
                request,
                "테스트 자산 공모",
                asset.unitPrice()
        )).thenReturn(expectedResponse);

        // when
        OfferingCreateResponse response =
                offeringCommandService.create(
                        issuerId,
                        request
                );

        // then
        assertThat(response).isSameAs(expectedResponse);

        verify(userServiceClient)
                .getInvestmentEligibility(issuerId);

        verify(assetServiceClient)
                .getAsset("SYSTEM", assetId);

        verify(offeringTransactionService)
                .createOffering(
                        issuerId,
                        request,
                        "테스트 자산 공모",
                        asset.unitPrice()
                );
    }

    @Test
    @DisplayName("ISSUER가 아닌 사용자는 공모를 생성할 수 없다")
    void rejectsOfferingCreationByNonIssuer() {
        // given
        UUID userId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        OfferingCreateRequest request = createRequest(assetId);

        UserInvestmentEligibilityResponse user =
                new UserInvestmentEligibilityResponse(
                        userId,
                        "INVESTOR",
                        "ACTIVE",
                        "VERIFIED",
                        Instant.now().plusSeconds(3_600)
                );

        when(userServiceClient.getInvestmentEligibility(userId))
                .thenReturn(ApiResponse.success(
                        user,
                        "사용자 조회 성공"
                ));

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringCommandService.create(
                        userId,
                        request
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        OfferingErrorCode
                                .OFFERING_ISSUER_ELIGIBILITY_NOT_MET
                );

        verifyNoInteractions(assetServiceClient);
        verifyNoInteractions(offeringTransactionService);
    }

    @Test
    @DisplayName("User 응답의 사용자 ID가 요청한 ISSUER와 다르면 외부 응답 오류로 처리한다")
    void rejectsMismatchedUserResponse() {
        // given
        UUID requestedIssuerId = UUID.randomUUID();
        UUID responseUserId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        OfferingCreateRequest request = createRequest(assetId);

        UserInvestmentEligibilityResponse user =
                eligibleIssuer(responseUserId);

        when(userServiceClient
                .getInvestmentEligibility(requestedIssuerId))
                .thenReturn(ApiResponse.success(
                        user,
                        "사용자 조회 성공"
                ));

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringCommandService.create(
                        requestedIssuerId,
                        request
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        OfferingErrorCode.USER_RESPONSE_INVALID
                );

        verifyNoInteractions(assetServiceClient);
        verifyNoInteractions(offeringTransactionService);
    }

    @Test
    @DisplayName("심사 요청 시 ISSUER의 최신 자격이 유효하지 않으면 Asset을 조회하지 않는다")
    void rejectsReviewRequestByIneligibleIssuer() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();

        Offering offering = mock(Offering.class);

        when(offering.getIssuerId())
                .thenReturn(issuerId);

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        UserInvestmentEligibilityResponse suspendedIssuer =
                new UserInvestmentEligibilityResponse(
                        issuerId,
                        "ISSUER",
                        "SUSPENDED",
                        "VERIFIED",
                        Instant.now().plusSeconds(3_600)
                );

        when(userServiceClient.getInvestmentEligibility(issuerId))
                .thenReturn(ApiResponse.success(
                        suspendedIssuer,
                        "사용자 조회 성공"
                ));

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringCommandService.requestReview(
                        offeringId,
                        issuerId
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        OfferingErrorCode
                                .OFFERING_ISSUER_ELIGIBILITY_NOT_MET
                );

        verifyNoInteractions(assetServiceClient);
        verifyNoInteractions(offeringTransactionService);
    }

    @Test
    @DisplayName("ISSUER가 공모를 수정할 때 최신 사용자 자격을 검증한다")
    void rejectsOfferingUpdateByIneligibleIssuer() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();
        Offering offering = mock(Offering.class);

        when(offering.getIssuerId())
                .thenReturn(issuerId);

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        UserInvestmentEligibilityResponse expiredIssuer =
                new UserInvestmentEligibilityResponse(
                        issuerId,
                        "ISSUER",
                        "ACTIVE",
                        "VERIFIED",
                        Instant.now().minusSeconds(1)
                );

        when(userServiceClient.getInvestmentEligibility(issuerId))
                .thenReturn(ApiResponse.success(
                        expiredIssuer,
                        "사용자 조회 성공"
                ));

        OfferingUpdateRequest request =
                emptyUpdateRequest();

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringCommandService.updateOffering(
                        offeringId,
                        issuerId,
                        "ISSUER",
                        request
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        OfferingErrorCode
                                .OFFERING_ISSUER_ELIGIBILITY_NOT_MET
                );

        verifyNoInteractions(assetServiceClient);
        verifyNoInteractions(offeringTransactionService);
    }

    @Test
    @DisplayName("ADMIN이 공모를 수정할 때 ISSUER 사용자 자격을 조회하지 않는다")
    void updatesOfferingByAdminWithoutIssuerEligibilityCheck() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        Offering offering = mock(Offering.class);

        when(offering.getIssuerId())
                .thenReturn(issuerId);

        when(offering.getAssetId())
                .thenReturn(assetId);

        when(offering.getTotalQuantity())
                .thenReturn(100L);

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        AssetOfferingInfoResponse asset =
                availableAsset(assetId, issuerId);

        when(assetServiceClient.getAsset("SYSTEM",assetId))
                .thenReturn(ApiResponse.success(
                        asset,
                        "자산 조회 성공"
                ));

        OfferingUpdateRequest request =
                emptyUpdateRequest();

        OfferingUpdateResponse expectedResponse =
                mock(OfferingUpdateResponse.class);

        when(offeringTransactionService.updateOffering(
                offeringId,
                adminId,
                "ADMIN",
                request
        )).thenReturn(expectedResponse);

        // when
        OfferingUpdateResponse response =
                offeringCommandService.updateOffering(
                        offeringId,
                        adminId,
                        "ADMIN",
                        request
                );

        // then
        assertThat(response).isSameAs(expectedResponse);

        verifyNoInteractions(userServiceClient);

        verify(assetServiceClient)
                .getAsset("SYSTEM",assetId);

        verify(offeringTransactionService)
                .updateOffering(
                        offeringId,
                        adminId,
                        "ADMIN",
                        request
                );
    }

    private OfferingCreateRequest createRequest(UUID assetId) {
        LocalDateTime now = LocalDateTime.now();

        return new OfferingCreateRequest(
                assetId,
                100L,
                1L,
                10L,
                now.plusHours(1),
                now.plusHours(2)
        );
    }

    private OfferingUpdateRequest emptyUpdateRequest() {
        return new OfferingUpdateRequest(
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    @Test
    @DisplayName("유효한 ISSUER는 최신 사용자 자격 검증 후 공모 삭제를 요청한다")
    void deletesOfferingForEligibleIssuer() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();

        Offering offering = mock(Offering.class);

        when(offering.getIssuerId())
                .thenReturn(issuerId);

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        when(userServiceClient.getInvestmentEligibility(issuerId))
                .thenReturn(ApiResponse.success(
                        eligibleIssuer(issuerId),
                        "사용자 조회 성공"
                ));

        OfferingDeleteResponse expectedResponse =
                mock(OfferingDeleteResponse.class);

        when(offeringTransactionService.deleteOffering(
                offeringId,
                issuerId,
                "ISSUER"
        )).thenReturn(expectedResponse);

        // when
        OfferingDeleteResponse response =
                offeringCommandService.deleteOffering(
                        offeringId,
                        issuerId,
                        "ISSUER"
                );

        // then
        assertThat(response).isSameAs(expectedResponse);

        verify(userServiceClient)
                .getInvestmentEligibility(issuerId);

        verify(offeringTransactionService)
                .deleteOffering(
                        offeringId,
                        issuerId,
                        "ISSUER"
                );

        verifyNoInteractions(assetServiceClient);
    }

    @Test
    @DisplayName("자격이 유효하지 않은 ISSUER는 공모를 삭제할 수 없다")
    void rejectsOfferingDeletionByIneligibleIssuer() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();

        Offering offering = mock(Offering.class);

        when(offering.getIssuerId())
                .thenReturn(issuerId);

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        UserInvestmentEligibilityResponse expiredIssuer =
                new UserInvestmentEligibilityResponse(
                        issuerId,
                        "ISSUER",
                        "ACTIVE",
                        "VERIFIED",
                        Instant.now().minusSeconds(1)
                );

        when(userServiceClient.getInvestmentEligibility(issuerId))
                .thenReturn(ApiResponse.success(
                        expiredIssuer,
                        "사용자 조회 성공"
                ));

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringCommandService.deleteOffering(
                        offeringId,
                        issuerId,
                        "ISSUER"
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        OfferingErrorCode
                                .OFFERING_ISSUER_ELIGIBILITY_NOT_MET
                );

        verifyNoInteractions(offeringTransactionService);
        verifyNoInteractions(assetServiceClient);
    }

    @Test
    @DisplayName("ADMIN은 ISSUER 사용자 자격을 조회하지 않고 공모 삭제를 요청한다")
    void deletesOfferingByAdminWithoutIssuerEligibilityCheck() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        Offering offering = mock(Offering.class);

        when(offeringRepository
                .findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        OfferingDeleteResponse expectedResponse =
                mock(OfferingDeleteResponse.class);

        when(offeringTransactionService.deleteOffering(
                offeringId,
                adminId,
                "ADMIN"
        )).thenReturn(expectedResponse);

        // when
        OfferingDeleteResponse response =
                offeringCommandService.deleteOffering(
                        offeringId,
                        adminId,
                        "ADMIN"
                );

        // then
        assertThat(response).isSameAs(expectedResponse);

        verifyNoInteractions(userServiceClient);
        verifyNoInteractions(assetServiceClient);

        verify(offeringTransactionService)
                .deleteOffering(
                        offeringId,
                        adminId,
                        "ADMIN"
                );
    }

    @Test
    @DisplayName("자산명의 앞뒤 Unicode 공백을 제거하여 공모 제목을 생성한다")
    void stripsUnicodeWhitespaceFromOfferingTitle() {
        // given
        UUID issuerId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        OfferingCreateRequest request =
                createRequest(assetId);

        AssetOfferingInfoResponse asset =
                new AssetOfferingInfoResponse(
                        assetId,
                        issuerId,
                        "REAL_ESTATE",

                        // U+2003 EM SPACE를 자산명 앞뒤에 배치
                        "\u2003테스트 자산\u2003",

                        10_000L,
                        1_000L,
                        0L,
                        "APPROVED"
                );

        OfferingCreateResponse expectedResponse =
                mock(OfferingCreateResponse.class);

        when(userServiceClient.getInvestmentEligibility(issuerId))
                .thenReturn(ApiResponse.success(
                        eligibleIssuer(issuerId),
                        "사용자 조회 성공"
                ));

        when(assetServiceClient.getAsset("SYSTEM", assetId))
                .thenReturn(ApiResponse.success(
                        asset,
                        "자산 조회 성공"
                ));

        when(offeringTransactionService.createOffering(
                issuerId,
                request,
                "테스트 자산 공모",
                asset.unitPrice()
        )).thenReturn(expectedResponse);

        // when
        OfferingCreateResponse response =
                offeringCommandService.create(
                        issuerId,
                        request
                );

        // then
        assertThat(response).isSameAs(expectedResponse);

        verify(offeringTransactionService)
                .createOffering(
                        issuerId,
                        request,
                        "테스트 자산 공모",
                        asset.unitPrice()
                );
    }

    private UserInvestmentEligibilityResponse eligibleIssuer(
            UUID issuerId
    ) {
        return new UserInvestmentEligibilityResponse(
                issuerId,
                "ISSUER",
                "ACTIVE",
                "VERIFIED",
                Instant.now().plusSeconds(3_600)
        );
    }

    private AssetOfferingInfoResponse availableAsset(
            UUID assetId,
            UUID issuerId
    ) {
        return new AssetOfferingInfoResponse(
                assetId,
                issuerId,
                "REAL_ESTATE",
                "테스트 자산",
                10_000L,
                1_000L,
                0L,
                "APPROVED"
        );
    }
}