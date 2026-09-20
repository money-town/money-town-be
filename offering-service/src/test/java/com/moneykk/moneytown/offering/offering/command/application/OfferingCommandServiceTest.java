package com.moneykk.moneytown.offering.offering.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.offering.command.dto.request.OfferingCreateRequest;
import com.moneykk.moneytown.offering.offering.command.dto.request.OfferingRejectionRequest;
import com.moneykk.moneytown.offering.offering.command.dto.request.OfferingUpdateRequest;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingApprovalResponse;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingCreateResponse;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingRejectionResponse;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingReviewRequestResponse;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingUpdateResponse;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingDeleteResponse;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.offering.infrastructure.client.AssetServiceClient;
import com.moneykk.moneytown.offering.offering.infrastructure.client.dto.AssetOfferingInfoResponse;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.UserServiceClient;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.UserInvestmentEligibilityResponse;
import feign.FeignException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
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
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Mock
    private BulkheadRegistry bulkheadRegistry;

    @Mock
    private OfferingTransactionService offeringTransactionService;

    @InjectMocks
    private OfferingCommandService offeringCommandService;

    @BeforeEach
    void setUp() {
        lenient().when(circuitBreakerRegistry.circuitBreaker(anyString()))
                .thenReturn(CircuitBreaker.ofDefaults("user-service"));
        lenient().when(bulkheadRegistry.bulkhead(anyString()))
                .thenReturn(Bulkhead.ofDefaults("user-service"));
    }

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

    @Test
    @DisplayName("소유자인 ISSUER가 유효한 자산으로 공모 심사를 요청한다")
    void requestsReviewForOwningIssuer() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        Offering offering = mock(Offering.class);
        when(offering.getIssuerId()).thenReturn(issuerId);
        when(offering.getAssetId()).thenReturn(assetId);

        when(offeringRepository.findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        when(userServiceClient.getInvestmentEligibility(issuerId))
                .thenReturn(ApiResponse.success(
                        eligibleIssuer(issuerId), "사용자 조회 성공"
                ));

        when(assetServiceClient.getAsset("SYSTEM", assetId))
                .thenReturn(ApiResponse.success(
                        availableAsset(assetId, issuerId), "자산 조회 성공"
                ));

        OfferingReviewRequestResponse expectedResponse =
                mock(OfferingReviewRequestResponse.class);

        when(offeringTransactionService.requestReview(offeringId, issuerId))
                .thenReturn(expectedResponse);

        // when
        OfferingReviewRequestResponse response =
                offeringCommandService.requestReview(offeringId, issuerId);

        // then
        assertThat(response).isSameAs(expectedResponse);
        verify(offeringTransactionService).requestReview(offeringId, issuerId);
    }

    @Test
    @DisplayName("공모 소유자가 아닌 ISSUER는 심사를 요청할 수 없다")
    void rejectsReviewRequestByNonOwner() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherIssuerId = UUID.randomUUID();

        Offering offering = mock(Offering.class);
        when(offering.getIssuerId()).thenReturn(ownerId);

        when(offeringRepository.findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringCommandService.requestReview(
                        offeringId, otherIssuerId
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_ACCESS_DENIED);

        verifyNoInteractions(userServiceClient, assetServiceClient, offeringTransactionService);
    }

    @Test
    @DisplayName("심사 요청 시 자산이 APPROVED 상태가 아니면 거부한다")
    void rejectsReviewRequestWhenAssetNotApproved() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        Offering offering = mock(Offering.class);
        when(offering.getIssuerId()).thenReturn(issuerId);
        when(offering.getAssetId()).thenReturn(assetId);

        when(offeringRepository.findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        when(userServiceClient.getInvestmentEligibility(issuerId))
                .thenReturn(ApiResponse.success(
                        eligibleIssuer(issuerId), "사용자 조회 성공"
                ));

        AssetOfferingInfoResponse pendingAsset = new AssetOfferingInfoResponse(
                assetId, issuerId, "REAL_ESTATE", "테스트 자산",
                10_000L, 1_000L, 0L, "PENDING"
        );

        when(assetServiceClient.getAsset("SYSTEM", assetId))
                .thenReturn(ApiResponse.success(pendingAsset, "자산 조회 성공"));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringCommandService.requestReview(offeringId, issuerId)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_ASSET_NOT_AVAILABLE);

        verifyNoInteractions(offeringTransactionService);
    }

    @Test
    @DisplayName("심사 요청 시 자산 소유자가 다르면 거부한다")
    void rejectsReviewRequestWhenAssetOwnerMismatches() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();

        Offering offering = mock(Offering.class);
        when(offering.getIssuerId()).thenReturn(issuerId);
        when(offering.getAssetId()).thenReturn(assetId);

        when(offeringRepository.findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        when(userServiceClient.getInvestmentEligibility(issuerId))
                .thenReturn(ApiResponse.success(
                        eligibleIssuer(issuerId), "사용자 조회 성공"
                ));

        when(assetServiceClient.getAsset("SYSTEM", assetId))
                .thenReturn(ApiResponse.success(
                        availableAsset(assetId, otherOwnerId), "자산 조회 성공"
                ));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringCommandService.requestReview(offeringId, issuerId)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_ASSET_ACCESS_DENIED);
    }

    @Test
    @DisplayName("심사 요청 시 자산을 찾을 수 없으면 거부한다")
    void rejectsReviewRequestWhenAssetNotFound() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        Offering offering = mock(Offering.class);
        when(offering.getIssuerId()).thenReturn(issuerId);
        when(offering.getAssetId()).thenReturn(assetId);

        when(offeringRepository.findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        when(userServiceClient.getInvestmentEligibility(issuerId))
                .thenReturn(ApiResponse.success(
                        eligibleIssuer(issuerId), "사용자 조회 성공"
                ));

        when(assetServiceClient.getAsset("SYSTEM", assetId))
                .thenThrow(mock(FeignException.NotFound.class));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringCommandService.requestReview(offeringId, issuerId)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_ASSET_NOT_FOUND);
    }

    @Test
    @DisplayName("심사 요청 시 자산 서비스 호출이 실패하면 거부한다")
    void rejectsReviewRequestWhenAssetServiceUnavailable() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        Offering offering = mock(Offering.class);
        when(offering.getIssuerId()).thenReturn(issuerId);
        when(offering.getAssetId()).thenReturn(assetId);

        when(offeringRepository.findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        when(userServiceClient.getInvestmentEligibility(issuerId))
                .thenReturn(ApiResponse.success(
                        eligibleIssuer(issuerId), "사용자 조회 성공"
                ));

        when(assetServiceClient.getAsset("SYSTEM", assetId))
                .thenThrow(mock(FeignException.class));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringCommandService.requestReview(offeringId, issuerId)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.ASSET_SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("존재하는 공모를 승인하면 승인 결과를 반환한다")
    void approvesExistingOffering() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        Offering offering = mock(Offering.class);
        when(offering.getOfferingId()).thenReturn(offeringId);
        when(offering.getOfferingStatus()).thenReturn(OfferingStatus.SCHEDULED);
        when(offering.getReviewedAt()).thenReturn(Instant.now());
        when(offering.getReviewedBy()).thenReturn(reviewerId);

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));

        // when
        OfferingApprovalResponse response =
                offeringCommandService.approveOffering(offeringId, reviewerId);

        // then
        assertThat(response.offeringId()).isEqualTo(offeringId);
        assertThat(response.offeringStatus()).isEqualTo(OfferingStatus.SCHEDULED);
        assertThat(response.reviewedBy()).isEqualTo(reviewerId);

        verify(offering).approve(reviewerId);
    }

    @Test
    @DisplayName("승인 대상 공모를 찾을 수 없으면 거부한다")
    void rejectsApprovalWhenOfferingNotFound() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringCommandService.approveOffering(offeringId, reviewerId)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하는 공모를 반려하면 반려 결과를 반환한다")
    void rejectsExistingOffering() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        OfferingRejectionRequest request =
                new OfferingRejectionRequest("자산 증빙 부족");

        Offering offering = mock(Offering.class);
        when(offering.getOfferingId()).thenReturn(offeringId);
        when(offering.getOfferingStatus()).thenReturn(OfferingStatus.REJECTED);
        when(offering.getRejectionReason()).thenReturn("자산 증빙 부족");
        when(offering.getReviewedAt()).thenReturn(Instant.now());
        when(offering.getReviewedBy()).thenReturn(reviewerId);

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));

        // when
        OfferingRejectionResponse response =
                offeringCommandService.rejectOffering(
                        offeringId, reviewerId, request
                );

        // then
        assertThat(response.offeringId()).isEqualTo(offeringId);
        assertThat(response.offeringStatus()).isEqualTo(OfferingStatus.REJECTED);
        assertThat(response.rejectionReason()).isEqualTo("자산 증빙 부족");

        verify(offering).reject(reviewerId, request.rejectionReason());
    }

    @Test
    @DisplayName("반려 대상 공모를 찾을 수 없으면 거부한다")
    void rejectsRejectionWhenOfferingNotFound() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        OfferingRejectionRequest request =
                new OfferingRejectionRequest("사유");

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringCommandService.rejectOffering(
                        offeringId, reviewerId, request
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_NOT_FOUND);
    }

    @Test
    @DisplayName("소유자인 ISSUER가 자격을 검증받아 공모를 수정한다")
    void updatesOfferingForOwningIssuer() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID issuerId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        Offering offering = mock(Offering.class);
        when(offering.getIssuerId()).thenReturn(issuerId);
        when(offering.getAssetId()).thenReturn(assetId);
        when(offering.getTotalQuantity()).thenReturn(100L);

        when(offeringRepository.findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        when(userServiceClient.getInvestmentEligibility(issuerId))
                .thenReturn(ApiResponse.success(
                        eligibleIssuer(issuerId), "사용자 조회 성공"
                ));

        AssetOfferingInfoResponse asset = availableAsset(assetId, issuerId);
        when(assetServiceClient.getAsset("SYSTEM", assetId))
                .thenReturn(ApiResponse.success(asset, "자산 조회 성공"));

        OfferingUpdateRequest request = emptyUpdateRequest();
        OfferingUpdateResponse expectedResponse =
                mock(OfferingUpdateResponse.class);

        when(offeringTransactionService.updateOffering(
                offeringId, issuerId, "ISSUER", request
        )).thenReturn(expectedResponse);

        // when
        OfferingUpdateResponse response = offeringCommandService.updateOffering(
                offeringId, issuerId, "ISSUER", request
        );

        // then
        assertThat(response).isSameAs(expectedResponse);
        verify(userServiceClient).getInvestmentEligibility(issuerId);
    }

    @Test
    @DisplayName("소유자도 관리자도 아니면 공모 수정을 거부한다")
    void rejectsUpdateWhenUserIsNeitherOwnerNorAdmin() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        Offering offering = mock(Offering.class);
        when(offering.getIssuerId()).thenReturn(ownerId);

        when(offeringRepository.findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringCommandService.updateOffering(
                        offeringId, otherUserId, "ISSUER", emptyUpdateRequest()
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_ACCESS_DENIED);

        verifyNoInteractions(userServiceClient, assetServiceClient, offeringTransactionService);
    }

    @Test
    @DisplayName("수정 대상 공모를 찾을 수 없으면 거부한다")
    void rejectsUpdateWhenOfferingNotFound() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(offeringRepository.findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringCommandService.updateOffering(
                        offeringId, userId, "ADMIN", emptyUpdateRequest()
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_NOT_FOUND);
    }

    @Test
    @DisplayName("소유자도 관리자도 아니면 공모 삭제를 거부한다")
    void rejectsDeleteWhenUserIsNeitherOwnerNorAdmin() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        Offering offering = mock(Offering.class);
        when(offering.getIssuerId()).thenReturn(ownerId);

        when(offeringRepository.findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.of(offering));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringCommandService.deleteOffering(
                        offeringId, otherUserId, "ISSUER"
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_ACCESS_DENIED);

        verifyNoInteractions(userServiceClient, offeringTransactionService);
    }

    @Test
    @DisplayName("삭제 대상 공모를 찾을 수 없으면 거부한다")
    void rejectsDeleteWhenOfferingNotFound() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(offeringRepository.findByOfferingIdAndIsDeletedFalse(offeringId))
                .thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringCommandService.deleteOffering(
                        offeringId, userId, "ADMIN"
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    @DisplayName("총 모집 수량이 0 이하이면 공모 생성을 거부한다")
    void rejectsCreateWhenTotalQuantityIsNotPositive(long totalQuantity) {
        // given
        UUID issuerId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        OfferingCreateRequest request = new OfferingCreateRequest(
                assetId, totalQuantity, 1L, 10L,
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(2)
        );

        when(userServiceClient.getInvestmentEligibility(issuerId))
                .thenReturn(ApiResponse.success(
                        eligibleIssuer(issuerId), "사용자 조회 성공"
                ));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringCommandService.create(issuerId, request)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.INVALID_OFFERING_QUANTITY);

        verifyNoInteractions(assetServiceClient);
    }

    @Test
    @DisplayName("자산을 찾을 수 없으면 공모 생성을 거부한다")
    void rejectsCreateWhenAssetNotFound() {
        // given
        UUID issuerId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        OfferingCreateRequest request = createRequest(assetId);

        when(userServiceClient.getInvestmentEligibility(issuerId))
                .thenReturn(ApiResponse.success(
                        eligibleIssuer(issuerId), "사용자 조회 성공"
                ));

        when(assetServiceClient.getAsset("SYSTEM", assetId))
                .thenThrow(mock(FeignException.NotFound.class));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringCommandService.create(issuerId, request)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_ASSET_NOT_FOUND);
    }

    @Test
    @DisplayName("자산 서비스 호출이 실패하면 공모 생성을 거부한다")
    void rejectsCreateWhenAssetServiceUnavailable() {
        // given
        UUID issuerId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        OfferingCreateRequest request = createRequest(assetId);

        when(userServiceClient.getInvestmentEligibility(issuerId))
                .thenReturn(ApiResponse.success(
                        eligibleIssuer(issuerId), "사용자 조회 성공"
                ));

        when(assetServiceClient.getAsset("SYSTEM", assetId))
                .thenThrow(mock(FeignException.class));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringCommandService.create(issuerId, request)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.ASSET_SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("자산 응답에 필수값이 없으면 공모 생성을 거부한다")
    void rejectsCreateWhenAssetResponseIsInvalid() {
        // given
        UUID issuerId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        OfferingCreateRequest request = createRequest(assetId);

        when(userServiceClient.getInvestmentEligibility(issuerId))
                .thenReturn(ApiResponse.success(
                        eligibleIssuer(issuerId), "사용자 조회 성공"
                ));

        AssetOfferingInfoResponse invalidAsset = new AssetOfferingInfoResponse(
                assetId, issuerId, "REAL_ESTATE", "테스트 자산",
                null, 1_000L, 0L, "APPROVED"
        );

        when(assetServiceClient.getAsset("SYSTEM", assetId))
                .thenReturn(ApiResponse.success(invalidAsset, "자산 조회 성공"));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringCommandService.create(issuerId, request)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.ASSET_RESPONSE_INVALID);
    }

    @Test
    @DisplayName("자산이 APPROVED 상태가 아니면 공모 생성을 거부한다")
    void rejectsCreateWhenAssetNotApproved() {
        // given
        UUID issuerId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        OfferingCreateRequest request = createRequest(assetId);

        when(userServiceClient.getInvestmentEligibility(issuerId))
                .thenReturn(ApiResponse.success(
                        eligibleIssuer(issuerId), "사용자 조회 성공"
                ));

        AssetOfferingInfoResponse pendingAsset = new AssetOfferingInfoResponse(
                assetId, issuerId, "REAL_ESTATE", "테스트 자산",
                10_000L, 1_000L, 0L, "PENDING"
        );

        when(assetServiceClient.getAsset("SYSTEM", assetId))
                .thenReturn(ApiResponse.success(pendingAsset, "자산 조회 성공"));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringCommandService.create(issuerId, request)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_ASSET_NOT_AVAILABLE);
    }

    @Test
    @DisplayName("자산 소유자가 요청한 ISSUER와 다르면 공모 생성을 거부한다")
    void rejectsCreateWhenAssetOwnerMismatches() {
        // given
        UUID issuerId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();
        OfferingCreateRequest request = createRequest(assetId);

        when(userServiceClient.getInvestmentEligibility(issuerId))
                .thenReturn(ApiResponse.success(
                        eligibleIssuer(issuerId), "사용자 조회 성공"
                ));

        when(assetServiceClient.getAsset("SYSTEM", assetId))
                .thenReturn(ApiResponse.success(
                        availableAsset(assetId, otherOwnerId), "자산 조회 성공"
                ));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringCommandService.create(issuerId, request)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_ASSET_ACCESS_DENIED);
    }

    @Test
    @DisplayName("배정된 지분 수량이 전체 발행 수량을 초과하면 공모 생성을 거부한다")
    void rejectsCreateWhenAllocatedQuantityExceedsTotalShareQuantity() {
        // given
        UUID issuerId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        OfferingCreateRequest request = createRequest(assetId);

        when(userServiceClient.getInvestmentEligibility(issuerId))
                .thenReturn(ApiResponse.success(
                        eligibleIssuer(issuerId), "사용자 조회 성공"
                ));

        AssetOfferingInfoResponse invalidStateAsset = new AssetOfferingInfoResponse(
                assetId, issuerId, "REAL_ESTATE", "테스트 자산",
                10_000L, 1_000L, 2_000L, "APPROVED"
        );

        when(assetServiceClient.getAsset("SYSTEM", assetId))
                .thenReturn(ApiResponse.success(invalidStateAsset, "자산 조회 성공"));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringCommandService.create(issuerId, request)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.ASSET_QUANTITY_STATE_INVALID);
    }

    @Test
    @DisplayName("요청 수량이 가용 지분 수량을 초과하면 공모 생성을 거부한다")
    void rejectsCreateWhenRequestedQuantityExceedsAvailable() {
        // given
        UUID issuerId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        // availableShareQuantity = 1_000 - 950 = 50, request totalQuantity = 100
        OfferingCreateRequest request = createRequest(assetId);

        when(userServiceClient.getInvestmentEligibility(issuerId))
                .thenReturn(ApiResponse.success(
                        eligibleIssuer(issuerId), "사용자 조회 성공"
                ));

        AssetOfferingInfoResponse limitedAsset = new AssetOfferingInfoResponse(
                assetId, issuerId, "REAL_ESTATE", "테스트 자산",
                10_000L, 1_000L, 950L, "APPROVED"
        );

        when(assetServiceClient.getAsset("SYSTEM", assetId))
                .thenReturn(ApiResponse.success(limitedAsset, "자산 조회 성공"));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringCommandService.create(issuerId, request)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_QUANTITY_EXCEEDS_AVAILABLE);
    }

    @Test
    @DisplayName("User Service에서 ISSUER를 찾을 수 없으면 공모 생성을 거부한다")
    void rejectsCreateWhenIssuerNotFound() {
        // given
        UUID issuerId = UUID.randomUUID();
        OfferingCreateRequest request = createRequest(UUID.randomUUID());

        when(userServiceClient.getInvestmentEligibility(issuerId))
                .thenThrow(mock(FeignException.NotFound.class));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringCommandService.create(issuerId, request)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.OFFERING_USER_NOT_FOUND);

        verifyNoInteractions(assetServiceClient);
    }

    @Test
    @DisplayName("User Service 호출이 실패하면 공모 생성을 거부한다")
    void rejectsCreateWhenUserServiceUnavailable() {
        // given
        UUID issuerId = UUID.randomUUID();
        OfferingCreateRequest request = createRequest(UUID.randomUUID());

        when(userServiceClient.getInvestmentEligibility(issuerId))
                .thenThrow(mock(FeignException.class));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringCommandService.create(issuerId, request)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.USER_SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("User Service 서킷이 열려 호출이 차단되면 공모 생성을 거부한다")
    void rejectsCreateWhenUserServiceCircuitIsOpen() {
        // given
        UUID issuerId = UUID.randomUUID();
        OfferingCreateRequest request = createRequest(UUID.randomUUID());

        when(userServiceClient.getInvestmentEligibility(issuerId))
                .thenThrow(CallNotPermittedException.createCallNotPermittedException(
                        CircuitBreaker.ofDefaults("user-service")
                ));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringCommandService.create(issuerId, request)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.USER_SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("User Service 동시 호출 제한(Bulkhead)에 걸리면 공모 생성을 거부한다")
    void rejectsCreateWhenUserServiceBulkheadIsFull() {
        // given
        UUID issuerId = UUID.randomUUID();
        OfferingCreateRequest request = createRequest(UUID.randomUUID());

        when(userServiceClient.getInvestmentEligibility(issuerId))
                .thenThrow(BulkheadFullException.createBulkheadFullException(
                        Bulkhead.ofDefaults("user-service")
                ));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringCommandService.create(issuerId, request)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.USER_SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("자산명이 비어 있으면 공모 제목을 생성할 수 없다")
    void rejectsCreateWhenAssetNameIsBlank() {
        // given
        UUID issuerId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        OfferingCreateRequest request = createRequest(assetId);

        when(userServiceClient.getInvestmentEligibility(issuerId))
                .thenReturn(ApiResponse.success(
                        eligibleIssuer(issuerId), "사용자 조회 성공"
                ));

        AssetOfferingInfoResponse blankNameAsset = new AssetOfferingInfoResponse(
                assetId, issuerId, "REAL_ESTATE", "   ",
                10_000L, 1_000L, 0L, "APPROVED"
        );

        when(assetServiceClient.getAsset("SYSTEM", assetId))
                .thenReturn(ApiResponse.success(blankNameAsset, "자산 조회 성공"));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> offeringCommandService.create(issuerId, request)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(OfferingErrorCode.ASSET_RESPONSE_INVALID);
    }

    @Test
    @DisplayName("자산명이 길면 공모 제목을 200자 이내로 잘라서 생성한다")
    void truncatesLongAssetNameInOfferingTitle() {
        // given
        UUID issuerId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        OfferingCreateRequest request = createRequest(assetId);

        String longAssetName = "가".repeat(300);

        AssetOfferingInfoResponse asset = new AssetOfferingInfoResponse(
                assetId, issuerId, "REAL_ESTATE", longAssetName,
                10_000L, 1_000L, 0L, "APPROVED"
        );

        String expectedTitle =
                longAssetName.substring(0, 200 - " 공모".length()) + " 공모";

        when(userServiceClient.getInvestmentEligibility(issuerId))
                .thenReturn(ApiResponse.success(
                        eligibleIssuer(issuerId), "사용자 조회 성공"
                ));

        when(assetServiceClient.getAsset("SYSTEM", assetId))
                .thenReturn(ApiResponse.success(asset, "자산 조회 성공"));

        OfferingCreateResponse expectedResponse =
                mock(OfferingCreateResponse.class);

        when(offeringTransactionService.createOffering(
                issuerId, request, expectedTitle, asset.unitPrice()
        )).thenReturn(expectedResponse);

        // when
        OfferingCreateResponse response =
                offeringCommandService.create(issuerId, request);

        // then
        assertThat(response).isSameAs(expectedResponse);
        assertThat(expectedTitle).hasSize(200);

        verify(offeringTransactionService).createOffering(
                issuerId, request, expectedTitle, asset.unitPrice()
        );
    }
}