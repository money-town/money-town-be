package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionCompensationResponse;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionCompensationResult;
import com.moneykk.moneytown.offering.subscription.domain.entity.IdempotencyOperation;
import com.moneykk.moneytown.offering.subscription.domain.entity.IdempotencyRequest;
import com.moneykk.moneytown.offering.subscription.domain.entity.IdempotencyRequestStatus;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.IdempotencyRequestRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.domain.service.SubscriptionRequestHasher;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.HoldingServiceClient;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.WalletServiceClient;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.HoldingSubscriptionStatusResponse;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.WalletHoldStatus;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.WalletHoldStatusResponse;
import feign.FeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionCompensationCommandServiceTest {

    private static final String RESOURCE_TYPE = "SUBSCRIPTION";
    private static final String SYSTEM_ROLE = "SYSTEM";

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private IdempotencyRequestRepository idempotencyRequestRepository;

    @Mock
    private SubscriptionIdempotencyService subscriptionIdempotencyService;

    @Mock
    private SubscriptionCompensationTransactionService
            subscriptionCompensationTransactionService;

    @Mock
    private SubscriptionRequestHasher subscriptionRequestHasher;

    @Mock
    private WalletServiceClient walletServiceClient;

    @Mock
    private HoldingServiceClient holdingServiceClient;

    @InjectMocks
    private SubscriptionCompensationCommandService service;

    @Test
    @DisplayName("MANUAL_REVIEW 청약의 외부 상태를 조회하고 보상 처리를 시작한다")
    void compensatesManualReviewSubscription() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "compensation-key";
        String correlationId = "correlation-id";
        String requestHash = "request-hash";

        Subscription subscription = mock(Subscription.class);

        WalletHoldStatusResponse walletStatus =
                walletStatus(subscriptionId);

        HoldingSubscriptionStatusResponse holdingStatus =
                holdingStatusWithoutAllocation(subscriptionId);

        SubscriptionCompensationResponse response =
                new SubscriptionCompensationResponse(
                        subscriptionId,
                        SubscriptionStatus.COMPENSATING
                );

        beginNewRequest(
                subscriptionId,
                adminId,
                idempotencyKey,
                requestHash
        );

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(subscriptionId))
                .thenReturn(Optional.of(subscription));

        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.MANUAL_REVIEW);

        when(walletServiceClient.getWalletHoldStatus(subscriptionId))
                .thenReturn(
                        ApiResponse.success(
                                walletStatus,
                                "Wallet Hold 조회 성공"
                        )
                );

        when(holdingServiceClient.getHoldingSubscriptionStatus(
                SYSTEM_ROLE,
                subscriptionId
        )).thenReturn(
                ApiResponse.success(
                        holdingStatus,
                        "Holding 처리 상태 조회 성공"
                )
        );

        when(subscriptionCompensationTransactionService.compensate(
                subscriptionId,
                adminId,
                idempotencyKey,
                correlationId,
                walletStatus,
                holdingStatus
        )).thenReturn(response);

        // when
        SubscriptionCompensationResult result =
                service.compensate(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        correlationId
                );

        // then
        assertThat(result.response()).isEqualTo(response);
        assertThat(result.replayed()).isFalse();

        verify(walletServiceClient)
                .getWalletHoldStatus(subscriptionId);

        verify(holdingServiceClient)
                .getHoldingSubscriptionStatus(
                        SYSTEM_ROLE,
                        subscriptionId
                );

        verify(subscriptionCompensationTransactionService)
                .compensate(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        correlationId,
                        walletStatus,
                        holdingStatus
                );

        verify(subscriptionIdempotencyService, never())
                .fail(
                        any(UUID.class),
                        any(String.class),
                        any(String.class),
                        any(Integer.class)
                );
    }

    @Test
    @DisplayName("Wallet Hold가 없다는 404 응답은 보상 가능한 상태로 처리한다")
    void treatsWalletNotFoundAsNoHold() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "wallet-not-found-key";
        String correlationId = "correlation-id";
        String requestHash = "request-hash";

        Subscription subscription = mock(Subscription.class);

        HoldingSubscriptionStatusResponse holdingStatus =
                holdingStatusWithoutAllocation(subscriptionId);

        SubscriptionCompensationResponse response =
                new SubscriptionCompensationResponse(
                        subscriptionId,
                        SubscriptionStatus.COMPENSATING
                );

        beginNewRequest(
                subscriptionId,
                adminId,
                idempotencyKey,
                requestHash
        );

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(subscriptionId))
                .thenReturn(Optional.of(subscription));

        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.MANUAL_REVIEW);

        FeignException.NotFound notFound =
                mock(FeignException.NotFound.class);

        when(walletServiceClient.getWalletHoldStatus(subscriptionId))
                .thenThrow(notFound);

        when(holdingServiceClient.getHoldingSubscriptionStatus(
                SYSTEM_ROLE,
                subscriptionId
        )).thenReturn(
                ApiResponse.success(
                        holdingStatus,
                        "Holding 처리 상태 조회 성공"
                )
        );

        when(subscriptionCompensationTransactionService.compensate(
                subscriptionId,
                adminId,
                idempotencyKey,
                correlationId,
                null,
                holdingStatus
        )).thenReturn(response);

        // when
        SubscriptionCompensationResult result =
                service.compensate(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        correlationId
                );

        // then
        assertThat(result.response()).isEqualTo(response);
        assertThat(result.replayed()).isFalse();

        verify(subscriptionCompensationTransactionService)
                .compensate(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        correlationId,
                        null,
                        holdingStatus
                );
    }

    @Test
    @DisplayName("동일 멱등키로 완료된 보상 요청은 기존 결과를 반환한다")
    void replaysCompletedIdempotentRequest() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "completed-key";
        String requestHash = "request-hash";

        IdempotencyRequest existing =
                mock(IdempotencyRequest.class);

        Subscription subscription =
                mock(Subscription.class);

        when(subscriptionRequestHasher
                .hashCompensation(subscriptionId))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class),
                eq(adminId),
                eq(IdempotencyOperation
                        .COMPENSATE_SUBSCRIPTION.name()),
                eq(idempotencyKey),
                eq(requestHash),
                eq(RESOURCE_TYPE)
        )).thenReturn(0);

        when(idempotencyRequestRepository
                .findByUserIdAndOperationAndIdempotencyKey(
                        adminId,
                        IdempotencyOperation.COMPENSATE_SUBSCRIPTION,
                        idempotencyKey
                ))
                .thenReturn(Optional.of(existing));

        when(existing.getRequestHash())
                .thenReturn(requestHash);

        when(existing.getIdempotencyRequestStatus())
                .thenReturn(IdempotencyRequestStatus.COMPLETED);

        when(existing.getResourceId())
                .thenReturn(subscriptionId);

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(subscriptionId))
                .thenReturn(Optional.of(subscription));

        when(subscription.getSubscriptionId())
                .thenReturn(subscriptionId);

        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.COMPENSATING);

        // when
        SubscriptionCompensationResult result =
                service.compensate(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        null
                );

        // then
        assertThat(result.replayed()).isTrue();
        assertThat(result.response().subscriptionId())
                .isEqualTo(subscriptionId);
        assertThat(result.response().subscriptionStatus())
                .isEqualTo(SubscriptionStatus.COMPENSATING);

        verifyNoInteractions(walletServiceClient);
        verifyNoInteractions(holdingServiceClient);
        verifyNoInteractions(
                subscriptionCompensationTransactionService
        );
    }

    @Test
    @DisplayName("MANUAL_REVIEW 상태가 아니면 관리자 보상 요청을 거부한다")
    void rejectsSubscriptionThatIsNotManualReview() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "invalid-status-key";
        String requestHash = "request-hash";

        Subscription subscription = mock(Subscription.class);

        beginNewRequest(
                subscriptionId,
                adminId,
                idempotencyKey,
                requestHash
        );

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(subscriptionId))
                .thenReturn(Optional.of(subscription));

        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.PROCESSING);

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.compensate(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        "correlation-id"
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        SubscriptionErrorCode
                                .SUBSCRIPTION_COMPENSATION_NOT_ALLOWED
                );

        verify(subscriptionIdempotencyService)
                .fail(
                        adminId,
                        IdempotencyOperation
                                .COMPENSATE_SUBSCRIPTION.name(),
                        idempotencyKey,
                        SubscriptionErrorCode
                                .SUBSCRIPTION_COMPENSATION_NOT_ALLOWED
                                .getStatus()
                                .value()
                );

        verifyNoInteractions(walletServiceClient);
        verifyNoInteractions(holdingServiceClient);
        verifyNoInteractions(
                subscriptionCompensationTransactionService
        );
    }

    @Test
    @DisplayName("Wallet 상태 조회 장애가 발생하면 보상 처리를 중단한다")
    void stopsCompensationWhenWalletServiceFails() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "wallet-failure-key";
        String requestHash = "request-hash";

        Subscription subscription = mock(Subscription.class);

        beginNewRequest(
                subscriptionId,
                adminId,
                idempotencyKey,
                requestHash
        );

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(subscriptionId))
                .thenReturn(Optional.of(subscription));

        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.MANUAL_REVIEW);

        when(walletServiceClient.getWalletHoldStatus(subscriptionId))
                .thenThrow(mock(FeignException.class));

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.compensate(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        "correlation-id"
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        SubscriptionErrorCode
                                .WALLET_SERVICE_UNAVAILABLE
                );

        verify(subscriptionIdempotencyService)
                .fail(
                        adminId,
                        IdempotencyOperation
                                .COMPENSATE_SUBSCRIPTION.name(),
                        idempotencyKey,
                        SubscriptionErrorCode
                                .WALLET_SERVICE_UNAVAILABLE
                                .getStatus()
                                .value()
                );

        verifyNoInteractions(holdingServiceClient);
        verifyNoInteractions(
                subscriptionCompensationTransactionService
        );
    }

    @Test
    @DisplayName("Holding 상태 조회 장애가 발생하면 보상 처리를 중단한다")
    void stopsCompensationWhenHoldingServiceFails() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "holding-failure-key";
        String requestHash = "request-hash";

        Subscription subscription = mock(Subscription.class);

        WalletHoldStatusResponse walletStatus =
                walletStatus(subscriptionId);

        beginNewRequest(
                subscriptionId,
                adminId,
                idempotencyKey,
                requestHash
        );

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(subscriptionId))
                .thenReturn(Optional.of(subscription));

        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.MANUAL_REVIEW);

        when(walletServiceClient.getWalletHoldStatus(subscriptionId))
                .thenReturn(
                        ApiResponse.success(
                                walletStatus,
                                "Wallet Hold 조회 성공"
                        )
                );

        when(holdingServiceClient.getHoldingSubscriptionStatus(
                SYSTEM_ROLE,
                subscriptionId
        )).thenThrow(mock(FeignException.class));

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.compensate(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        "correlation-id"
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        SubscriptionErrorCode
                                .HOLDING_SERVICE_UNAVAILABLE
                );

        verify(subscriptionIdempotencyService)
                .fail(
                        adminId,
                        IdempotencyOperation
                                .COMPENSATE_SUBSCRIPTION.name(),
                        idempotencyKey,
                        SubscriptionErrorCode
                                .HOLDING_SERVICE_UNAVAILABLE
                                .getStatus()
                                .value()
                );

        verifyNoInteractions(
                subscriptionCompensationTransactionService
        );
    }

    @Test
    @DisplayName("subscriptionId 또는 adminId가 없으면 보상 요청을 거부한다")
    void rejectsMissingSubscriptionIdOrAdminId() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.compensate(
                        null, UUID.randomUUID(), "key", "correlation-id"
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SubscriptionErrorCode.INVALID_SUBSCRIPTION_INPUT);

        verifyNoInteractions(subscriptionIdempotencyService);
    }

    @Test
    @DisplayName("Idempotency-Key가 비어 있으면 보상 요청을 거부한다")
    void rejectsBlankIdempotencyKey() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.compensate(
                        UUID.randomUUID(), UUID.randomUUID(), " ",
                        "correlation-id"
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SubscriptionErrorCode.INVALID_IDEMPOTENCY_KEY);

        verifyNoInteractions(subscriptionIdempotencyService);
    }

    @Test
    @DisplayName("이미 보상이 끝난 청약(REJECTED/CANCELLED)은 기존 결과를 재사용한다")
    void replaysAlreadyCompensatedSubscription() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String idempotencyKey = "already-compensated-key";
        String requestHash = "request-hash";

        Subscription subscription = mock(Subscription.class);
        SubscriptionCompensationResponse existingResponse =
                new SubscriptionCompensationResponse(
                        subscriptionId, SubscriptionStatus.REJECTED
                );

        beginNewRequest(
                subscriptionId, adminId, idempotencyKey, requestHash
        );

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(subscriptionId))
                .thenReturn(Optional.of(subscription));

        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.REJECTED);

        when(subscriptionCompensationTransactionService
                .completeExistingResult(
                        subscriptionId, adminId, idempotencyKey
                ))
                .thenReturn(existingResponse);

        // when
        SubscriptionCompensationResult result = service.compensate(
                subscriptionId, adminId, idempotencyKey, "correlation-id"
        );

        // then
        assertThat(result.replayed()).isTrue();
        assertThat(result.response()).isEqualTo(existingResponse);

        verifyNoInteractions(walletServiceClient);
        verifyNoInteractions(holdingServiceClient);
    }

    @Test
    @DisplayName("보상 대상 청약을 찾을 수 없으면 요청을 거부한다")
    void rejectsWhenSubscriptionNotFound() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String idempotencyKey = "not-found-key";
        String requestHash = "request-hash";

        beginNewRequest(
                subscriptionId, adminId, idempotencyKey, requestHash
        );

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(subscriptionId))
                .thenReturn(Optional.empty());

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.compensate(
                        subscriptionId, adminId, idempotencyKey,
                        "correlation-id"
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND);

        verify(subscriptionIdempotencyService).fail(
                adminId,
                IdempotencyOperation.COMPENSATE_SUBSCRIPTION.name(),
                idempotencyKey,
                SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND
                        .getStatus().value()
        );
    }

    @Test
    @DisplayName("동일 Idempotency-Key의 요청 해시가 다르면 충돌로 처리한다")
    void rejectsConflictingRequestHash() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String idempotencyKey = "conflict-key";
        String requestHash = "request-hash";

        when(subscriptionRequestHasher.hashCompensation(subscriptionId))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class), eq(adminId),
                eq(IdempotencyOperation.COMPENSATE_SUBSCRIPTION.name()),
                eq(idempotencyKey), eq(requestHash), eq(RESOURCE_TYPE)
        )).thenReturn(0);

        IdempotencyRequest existing = mock(IdempotencyRequest.class);
        when(existing.getRequestHash()).thenReturn("other-hash");

        when(idempotencyRequestRepository
                .findByUserIdAndOperationAndIdempotencyKey(
                        adminId,
                        IdempotencyOperation.COMPENSATE_SUBSCRIPTION,
                        idempotencyKey
                ))
                .thenReturn(Optional.of(existing));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.compensate(
                        subscriptionId, adminId, idempotencyKey, null
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SubscriptionErrorCode.IDEMPOTENCY_KEY_CONFLICT);
    }

    @Test
    @DisplayName("처리 중인 동일 보상 요청은 중복 실행하지 않는다")
    void rejectsProcessingRequest() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String idempotencyKey = "processing-key";
        String requestHash = "request-hash";

        when(subscriptionRequestHasher.hashCompensation(subscriptionId))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class), eq(adminId),
                eq(IdempotencyOperation.COMPENSATE_SUBSCRIPTION.name()),
                eq(idempotencyKey), eq(requestHash), eq(RESOURCE_TYPE)
        )).thenReturn(0);

        IdempotencyRequest existing = mock(IdempotencyRequest.class);
        when(existing.getRequestHash()).thenReturn(requestHash);
        when(existing.getIdempotencyRequestStatus())
                .thenReturn(IdempotencyRequestStatus.PROCESSING);

        when(idempotencyRequestRepository
                .findByUserIdAndOperationAndIdempotencyKey(
                        adminId,
                        IdempotencyOperation.COMPENSATE_SUBSCRIPTION,
                        idempotencyKey
                ))
                .thenReturn(Optional.of(existing));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.compensate(
                        subscriptionId, adminId, idempotencyKey, null
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(
                        SubscriptionErrorCode.IDEMPOTENCY_REQUEST_PROCESSING
                );
    }

    @Test
    @DisplayName("이전에 실패한 동일 보상 요청은 재시도를 거부한다")
    void rejectsFailedRequest() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String idempotencyKey = "failed-key";
        String requestHash = "request-hash";

        when(subscriptionRequestHasher.hashCompensation(subscriptionId))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class), eq(adminId),
                eq(IdempotencyOperation.COMPENSATE_SUBSCRIPTION.name()),
                eq(idempotencyKey), eq(requestHash), eq(RESOURCE_TYPE)
        )).thenReturn(0);

        IdempotencyRequest existing = mock(IdempotencyRequest.class);
        when(existing.getRequestHash()).thenReturn(requestHash);
        when(existing.getIdempotencyRequestStatus())
                .thenReturn(IdempotencyRequestStatus.FAILED);

        when(idempotencyRequestRepository
                .findByUserIdAndOperationAndIdempotencyKey(
                        adminId,
                        IdempotencyOperation.COMPENSATE_SUBSCRIPTION,
                        idempotencyKey
                ))
                .thenReturn(Optional.of(existing));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.compensate(
                        subscriptionId, adminId, idempotencyKey, null
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SubscriptionErrorCode.IDEMPOTENCY_REQUEST_FAILED);
    }

    @Test
    @DisplayName("동일 Idempotency-Key 요청 기록을 찾을 수 없으면 상태 오류로 처리한다")
    void rejectsWhenExistingRequestNotFound() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String idempotencyKey = "missing-record-key";
        String requestHash = "request-hash";

        when(subscriptionRequestHasher.hashCompensation(subscriptionId))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class), eq(adminId),
                eq(IdempotencyOperation.COMPENSATE_SUBSCRIPTION.name()),
                eq(idempotencyKey), eq(requestHash), eq(RESOURCE_TYPE)
        )).thenReturn(0);

        when(idempotencyRequestRepository
                .findByUserIdAndOperationAndIdempotencyKey(
                        adminId,
                        IdempotencyOperation.COMPENSATE_SUBSCRIPTION,
                        idempotencyKey
                ))
                .thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.compensate(
                        subscriptionId, adminId, idempotencyKey, null
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(
                        SubscriptionErrorCode.IDEMPOTENCY_REQUEST_STATE_INVALID
                );
    }

    @Test
    @DisplayName("완료된 요청에 연결된 리소스ID가 없으면 상태 오류로 처리한다")
    void rejectsCompletedRequestWithoutResourceId() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String idempotencyKey = "no-resource-key";
        String requestHash = "request-hash";

        when(subscriptionRequestHasher.hashCompensation(subscriptionId))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class), eq(adminId),
                eq(IdempotencyOperation.COMPENSATE_SUBSCRIPTION.name()),
                eq(idempotencyKey), eq(requestHash), eq(RESOURCE_TYPE)
        )).thenReturn(0);

        IdempotencyRequest existing = mock(IdempotencyRequest.class);
        when(existing.getRequestHash()).thenReturn(requestHash);
        when(existing.getIdempotencyRequestStatus())
                .thenReturn(IdempotencyRequestStatus.COMPLETED);
        when(existing.getResourceId()).thenReturn(null);

        when(idempotencyRequestRepository
                .findByUserIdAndOperationAndIdempotencyKey(
                        adminId,
                        IdempotencyOperation.COMPENSATE_SUBSCRIPTION,
                        idempotencyKey
                ))
                .thenReturn(Optional.of(existing));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.compensate(
                        subscriptionId, adminId, idempotencyKey, null
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(
                        SubscriptionErrorCode.IDEMPOTENCY_REQUEST_STATE_INVALID
                );
    }

    @Test
    @DisplayName("Wallet Hold 응답 데이터가 유효하지 않으면 외부 응답 오류로 처리한다")
    void rejectsInvalidWalletResponse() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String idempotencyKey = "invalid-wallet-key";
        String requestHash = "request-hash";

        Subscription subscription = mock(Subscription.class);

        beginNewRequest(
                subscriptionId, adminId, idempotencyKey, requestHash
        );

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(subscriptionId))
                .thenReturn(Optional.of(subscription));

        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.MANUAL_REVIEW);

        WalletHoldStatusResponse invalidStatus =
                new WalletHoldStatusResponse(
                        UUID.randomUUID(), 10_000L,
                        WalletHoldStatus.HELD, Instant.now()
                );

        when(walletServiceClient.getWalletHoldStatus(subscriptionId))
                .thenReturn(ApiResponse.success(
                        invalidStatus, "다른 청약의 Hold 정보"
                ));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.compensate(
                        subscriptionId, adminId, idempotencyKey,
                        "correlation-id"
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SubscriptionErrorCode.EXTERNAL_RESPONSE_INVALID);

        verifyNoInteractions(holdingServiceClient);
    }

    @Test
    @DisplayName("Holding 처리 상태 응답이 유효하지 않으면 외부 응답 오류로 처리한다")
    void rejectsInvalidHoldingResponse() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String idempotencyKey = "invalid-holding-key";
        String requestHash = "request-hash";

        Subscription subscription = mock(Subscription.class);
        WalletHoldStatusResponse walletStatus = walletStatus(subscriptionId);

        beginNewRequest(
                subscriptionId, adminId, idempotencyKey, requestHash
        );

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(subscriptionId))
                .thenReturn(Optional.of(subscription));

        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.MANUAL_REVIEW);

        when(walletServiceClient.getWalletHoldStatus(subscriptionId))
                .thenReturn(ApiResponse.success(
                        walletStatus, "Wallet Hold 조회 성공"
                ));

        HoldingSubscriptionStatusResponse invalidHoldingStatus =
                new HoldingSubscriptionStatusResponse(
                        UUID.randomUUID(),
                        null, null, null, 0L, 0L,
                        false, false, false, null
                );

        when(holdingServiceClient.getHoldingSubscriptionStatus(
                SYSTEM_ROLE, subscriptionId
        )).thenReturn(ApiResponse.success(
                invalidHoldingStatus, "다른 청약의 Holding 정보"
        ));

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.compensate(
                        subscriptionId, adminId, idempotencyKey,
                        "correlation-id"
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SubscriptionErrorCode.EXTERNAL_RESPONSE_INVALID);
    }

    @Test
    @DisplayName("예상하지 못한 런타임 예외가 발생하면 멱등 요청을 실패로 기록하고 예외를 다시 던진다")
    void recordsFailureAndRethrowsOnUnexpectedRuntimeException() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String idempotencyKey = "unexpected-error-key";
        String requestHash = "request-hash";

        beginNewRequest(
                subscriptionId, adminId, idempotencyKey, requestHash
        );

        RuntimeException unexpected = new RuntimeException("boom");

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(subscriptionId))
                .thenThrow(unexpected);

        // when & then
        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> service.compensate(
                        subscriptionId, adminId, idempotencyKey,
                        "correlation-id"
                )
        );

        assertThat(thrown).isSameAs(unexpected);

        verify(subscriptionIdempotencyService).fail(
                adminId,
                IdempotencyOperation.COMPENSATE_SUBSCRIPTION.name(),
                idempotencyKey,
                500
        );
    }

    private void beginNewRequest(
            UUID subscriptionId,
            UUID adminId,
            String idempotencyKey,
            String requestHash
    ) {
        when(subscriptionRequestHasher
                .hashCompensation(subscriptionId))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class),
                eq(adminId),
                eq(IdempotencyOperation
                        .COMPENSATE_SUBSCRIPTION.name()),
                eq(idempotencyKey),
                eq(requestHash),
                eq(RESOURCE_TYPE)
        )).thenReturn(1);
    }

    private WalletHoldStatusResponse walletStatus(
            UUID subscriptionId
    ) {
        return new WalletHoldStatusResponse(
                subscriptionId,
                10_000L,
                WalletHoldStatus.HELD,
                Instant.now()
        );
    }

    private HoldingSubscriptionStatusResponse
    holdingStatusWithoutAllocation(
            UUID subscriptionId
    ) {
        return new HoldingSubscriptionStatusResponse(
                subscriptionId,
                null,
                null,
                null,
                0L,
                0L,
                false,
                false,
                false,
                null
        );
    }
}