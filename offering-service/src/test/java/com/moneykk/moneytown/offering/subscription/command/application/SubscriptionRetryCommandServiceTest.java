package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionRetryResponse;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionRetryResult;
import com.moneykk.moneytown.offering.subscription.domain.entity.HoldingAllocationStatus;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionRetryCommandServiceTest {

    private static final String RESOURCE_TYPE = "SUBSCRIPTION";
    private static final String SYSTEM_ROLE = "SYSTEM";

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private IdempotencyRequestRepository
            idempotencyRequestRepository;

    @Mock
    private SubscriptionIdempotencyService
            subscriptionIdempotencyService;

    @Mock
    private SubscriptionRetryTransactionService
            subscriptionRetryTransactionService;

    @Mock
    private SubscriptionRequestHasher subscriptionRequestHasher;

    @Mock
    private WalletServiceClient walletServiceClient;

    @Mock
    private HoldingServiceClient holdingServiceClient;

    @InjectMocks
    private SubscriptionRetryCommandService service;

    @Test
    @DisplayName("MANUAL_REVIEW 청약의 외부 상태를 조회하고 재처리를 시작한다")
    void retriesManualReviewSubscription() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "retry-key";
        String correlationId = "correlation-id";
        String requestHash = "request-hash";

        Subscription subscription =
                manualReviewSubscription();

        WalletHoldStatusResponse walletStatus =
                walletStatus(
                        subscriptionId,
                        WalletHoldStatus.HELD
                );

        HoldingSubscriptionStatusResponse holdingStatus =
                holdingStatusWithoutAllocation(subscriptionId);

        SubscriptionRetryResponse response =
                new SubscriptionRetryResponse(
                        subscriptionId,
                        SubscriptionStatus.HOLD_SUCCEEDED
                );

        beginNewRequest(
                subscriptionId,
                adminId,
                idempotencyKey,
                requestHash
        );

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(
                        subscriptionId
                ))
                .thenReturn(Optional.of(subscription));

        when(walletServiceClient.getWalletHoldStatus(
                subscriptionId
        )).thenReturn(
                ApiResponse.success(
                        walletStatus,
                        "Wallet Hold 조회 성공"
                )
        );

        when(holdingServiceClient
                .getHoldingSubscriptionStatus(
                        SYSTEM_ROLE,
                        subscriptionId
                ))
                .thenReturn(
                        ApiResponse.success(
                                holdingStatus,
                                "Holding 처리 상태 조회 성공"
                        )
                );

        when(subscriptionRetryTransactionService.retry(
                subscriptionId,
                adminId,
                idempotencyKey,
                correlationId,
                walletStatus,
                holdingStatus
        )).thenReturn(response);

        // when
        SubscriptionRetryResult result =
                service.retry(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        correlationId
                );

        // then
        assertThat(result.replayed()).isFalse();
        assertThat(result.response()).isEqualTo(response);

        verify(walletServiceClient)
                .getWalletHoldStatus(subscriptionId);

        verify(holdingServiceClient)
                .getHoldingSubscriptionStatus(
                        SYSTEM_ROLE,
                        subscriptionId
                );

        verify(subscriptionRetryTransactionService)
                .retry(
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
                        anyString(),
                        anyString(),
                        any(Integer.class)
                );
    }

    @Test
    @DisplayName("CONFIRMED 상태에서 Holding 배정에 실패한 청약도 재처리한다")
    void retriesConfirmedHoldingFailure() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "holding-retry-key";
        String correlationId = "correlation-id";
        String requestHash = "request-hash";

        Subscription subscription = mock(Subscription.class);

        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.CONFIRMED);

        when(subscription.getHoldingAllocationStatus())
                .thenReturn(HoldingAllocationStatus.FAILED);

        when(subscription.isQuantityReserved())
                .thenReturn(true);

        WalletHoldStatusResponse walletStatus =
                walletStatus(
                        subscriptionId,
                        WalletHoldStatus.COMMITTED
                );

        HoldingSubscriptionStatusResponse holdingStatus =
                holdingStatusWithoutAllocation(subscriptionId);

        SubscriptionRetryResponse response =
                new SubscriptionRetryResponse(
                        subscriptionId,
                        SubscriptionStatus.CONFIRMED
                );

        beginNewRequest(
                subscriptionId,
                adminId,
                idempotencyKey,
                requestHash
        );

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(
                        subscriptionId
                ))
                .thenReturn(Optional.of(subscription));

        when(walletServiceClient.getWalletHoldStatus(
                subscriptionId
        )).thenReturn(
                ApiResponse.success(
                        walletStatus,
                        "Wallet Hold 조회 성공"
                )
        );

        when(holdingServiceClient
                .getHoldingSubscriptionStatus(
                        SYSTEM_ROLE,
                        subscriptionId
                ))
                .thenReturn(
                        ApiResponse.success(
                                holdingStatus,
                                "Holding 처리 상태 조회 성공"
                        )
                );

        when(subscriptionRetryTransactionService.retry(
                subscriptionId,
                adminId,
                idempotencyKey,
                correlationId,
                walletStatus,
                holdingStatus
        )).thenReturn(response);

        // when
        SubscriptionRetryResult result =
                service.retry(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        correlationId
                );

        // then
        assertThat(result.replayed()).isFalse();
        assertThat(result.response()).isEqualTo(response);

        verify(subscriptionRetryTransactionService)
                .retry(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        correlationId,
                        walletStatus,
                        holdingStatus
                );
    }

    @Test
    @DisplayName("Wallet Hold가 없다는 404 응답은 Hold 미생성 상태로 처리한다")
    void treatsWalletNotFoundAsMissingHold() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "wallet-not-found-key";
        String correlationId = "correlation-id";
        String requestHash = "request-hash";

        Subscription subscription =
                manualReviewSubscription();

        HoldingSubscriptionStatusResponse holdingStatus =
                holdingStatusWithoutAllocation(subscriptionId);

        SubscriptionRetryResponse response =
                new SubscriptionRetryResponse(
                        subscriptionId,
                        SubscriptionStatus.PROCESSING
                );

        beginNewRequest(
                subscriptionId,
                adminId,
                idempotencyKey,
                requestHash
        );

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(
                        subscriptionId
                ))
                .thenReturn(Optional.of(subscription));

        FeignException.NotFound notFound =
                mock(FeignException.NotFound.class);

        when(walletServiceClient.getWalletHoldStatus(
                subscriptionId
        )).thenThrow(notFound);

        when(holdingServiceClient
                .getHoldingSubscriptionStatus(
                        SYSTEM_ROLE,
                        subscriptionId
                ))
                .thenReturn(
                        ApiResponse.success(
                                holdingStatus,
                                "Holding 처리 상태 조회 성공"
                        )
                );

        when(subscriptionRetryTransactionService.retry(
                subscriptionId,
                adminId,
                idempotencyKey,
                correlationId,
                null,
                holdingStatus
        )).thenReturn(response);

        // when
        SubscriptionRetryResult result =
                service.retry(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        correlationId
                );

        // then
        assertThat(result.replayed()).isFalse();
        assertThat(result.response()).isEqualTo(response);

        verify(subscriptionRetryTransactionService)
                .retry(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        correlationId,
                        null,
                        holdingStatus
                );
    }

    @Test
    @DisplayName("Correlation-ID가 없으면 서버에서 생성하여 재처리에 전달한다")
    void generatesCorrelationIdWhenMissing() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "missing-correlation-key";
        String requestHash = "request-hash";

        Subscription subscription =
                manualReviewSubscription();

        WalletHoldStatusResponse walletStatus =
                walletStatus(
                        subscriptionId,
                        WalletHoldStatus.HELD
                );

        HoldingSubscriptionStatusResponse holdingStatus =
                holdingStatusWithoutAllocation(subscriptionId);

        SubscriptionRetryResponse response =
                new SubscriptionRetryResponse(
                        subscriptionId,
                        SubscriptionStatus.HOLD_SUCCEEDED
                );

        beginNewRequest(
                subscriptionId,
                adminId,
                idempotencyKey,
                requestHash
        );

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(
                        subscriptionId
                ))
                .thenReturn(Optional.of(subscription));

        when(walletServiceClient.getWalletHoldStatus(
                subscriptionId
        )).thenReturn(
                ApiResponse.success(
                        walletStatus,
                        "Wallet Hold 조회 성공"
                )
        );

        when(holdingServiceClient
                .getHoldingSubscriptionStatus(
                        SYSTEM_ROLE,
                        subscriptionId
                ))
                .thenReturn(
                        ApiResponse.success(
                                holdingStatus,
                                "Holding 처리 상태 조회 성공"
                        )
                );

        when(subscriptionRetryTransactionService.retry(
                eq(subscriptionId),
                eq(adminId),
                eq(idempotencyKey),
                anyString(),
                eq(walletStatus),
                eq(holdingStatus)
        )).thenReturn(response);

        // when
        service.retry(
                subscriptionId,
                adminId,
                idempotencyKey,
                null
        );

        // then
        ArgumentCaptor<String> correlationIdCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(subscriptionRetryTransactionService)
                .retry(
                        eq(subscriptionId),
                        eq(adminId),
                        eq(idempotencyKey),
                        correlationIdCaptor.capture(),
                        eq(walletStatus),
                        eq(holdingStatus)
                );

        assertThat(correlationIdCaptor.getValue())
                .isNotBlank();

        assertDoesNotThrow(() ->
                UUID.fromString(
                        correlationIdCaptor.getValue()
                )
        );
    }

    @Test
    @DisplayName("완료된 동일 멱등키 요청은 외부 조회 없이 기존 결과를 반환한다")
    void returnsCompletedIdempotentResult() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "completed-retry-key";
        String requestHash = "request-hash";

        IdempotencyRequest existing =
                mock(IdempotencyRequest.class);

        Subscription subscription =
                mock(Subscription.class);

        when(subscriptionRequestHasher.hashRetry(subscriptionId))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class),
                eq(adminId),
                eq(IdempotencyOperation.RETRY_SUBSCRIPTION.name()),
                eq(idempotencyKey),
                eq(requestHash),
                eq(RESOURCE_TYPE)
        )).thenReturn(0);

        when(idempotencyRequestRepository
                .findByUserIdAndOperationAndIdempotencyKey(
                        adminId,
                        IdempotencyOperation.RETRY_SUBSCRIPTION,
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
                .findBySubscriptionIdAndIsDeletedFalse(
                        subscriptionId
                ))
                .thenReturn(Optional.of(subscription));

        when(subscription.getSubscriptionId())
                .thenReturn(subscriptionId);

        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.HOLD_SUCCEEDED);

        // when
        SubscriptionRetryResult result =
                service.retry(
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
                .isEqualTo(SubscriptionStatus.HOLD_SUCCEEDED);

        verifyNoInteractions(walletServiceClient);
        verifyNoInteractions(holdingServiceClient);
        verifyNoInteractions(
                subscriptionRetryTransactionService
        );
    }

    @Test
    @DisplayName("재처리 대상 상태가 아니면 외부 상태를 조회하지 않는다")
    void rejectsNonRetryableSubscription() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "invalid-status-key";
        String requestHash = "request-hash";

        Subscription subscription =
                mock(Subscription.class);

        beginNewRequest(
                subscriptionId,
                adminId,
                idempotencyKey,
                requestHash
        );

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(
                        subscriptionId
                ))
                .thenReturn(Optional.of(subscription));

        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.PROCESSING);

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.retry(
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
                                .SUBSCRIPTION_RETRY_NOT_ALLOWED
                );

        verify(subscriptionIdempotencyService)
                .fail(
                        adminId,
                        IdempotencyOperation.RETRY_SUBSCRIPTION.name(),
                        idempotencyKey,
                        SubscriptionErrorCode
                                .SUBSCRIPTION_RETRY_NOT_ALLOWED
                                .getStatus()
                                .value()
                );

        verifyNoInteractions(walletServiceClient);
        verifyNoInteractions(holdingServiceClient);
        verifyNoInteractions(
                subscriptionRetryTransactionService
        );
    }

    @Test
    @DisplayName("Wallet 상태 조회 장애가 발생하면 재처리를 중단한다")
    void stopsRetryWhenWalletServiceFails() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "wallet-failure-key";
        String requestHash = "request-hash";

        Subscription subscription =
                manualReviewSubscription();

        beginNewRequest(
                subscriptionId,
                adminId,
                idempotencyKey,
                requestHash
        );

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(
                        subscriptionId
                ))
                .thenReturn(Optional.of(subscription));

        when(walletServiceClient.getWalletHoldStatus(
                subscriptionId
        )).thenThrow(mock(FeignException.class));

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.retry(
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
                        IdempotencyOperation.RETRY_SUBSCRIPTION.name(),
                        idempotencyKey,
                        SubscriptionErrorCode
                                .WALLET_SERVICE_UNAVAILABLE
                                .getStatus()
                                .value()
                );

        verifyNoInteractions(holdingServiceClient);
        verifyNoInteractions(
                subscriptionRetryTransactionService
        );
    }

    @Test
    @DisplayName("Holding 상태 조회 장애가 발생하면 재처리를 중단한다")
    void stopsRetryWhenHoldingServiceFails() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "holding-failure-key";
        String requestHash = "request-hash";

        Subscription subscription =
                manualReviewSubscription();

        WalletHoldStatusResponse walletStatus =
                walletStatus(
                        subscriptionId,
                        WalletHoldStatus.HELD
                );

        beginNewRequest(
                subscriptionId,
                adminId,
                idempotencyKey,
                requestHash
        );

        when(subscriptionRepository
                .findBySubscriptionIdAndIsDeletedFalse(
                        subscriptionId
                ))
                .thenReturn(Optional.of(subscription));

        when(walletServiceClient.getWalletHoldStatus(
                subscriptionId
        )).thenReturn(
                ApiResponse.success(
                        walletStatus,
                        "Wallet Hold 조회 성공"
                )
        );

        when(holdingServiceClient
                .getHoldingSubscriptionStatus(
                        SYSTEM_ROLE,
                        subscriptionId
                ))
                .thenThrow(mock(FeignException.class));

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.retry(
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
                        IdempotencyOperation.RETRY_SUBSCRIPTION.name(),
                        idempotencyKey,
                        SubscriptionErrorCode
                                .HOLDING_SERVICE_UNAVAILABLE
                                .getStatus()
                                .value()
                );

        verifyNoInteractions(
                subscriptionRetryTransactionService
        );
    }

    private void beginNewRequest(
            UUID subscriptionId,
            UUID adminId,
            String idempotencyKey,
            String requestHash
    ) {
        when(subscriptionRequestHasher.hashRetry(subscriptionId))
                .thenReturn(requestHash);

        when(subscriptionIdempotencyService.tryBegin(
                any(UUID.class),
                eq(adminId),
                eq(IdempotencyOperation.RETRY_SUBSCRIPTION.name()),
                eq(idempotencyKey),
                eq(requestHash),
                eq(RESOURCE_TYPE)
        )).thenReturn(1);
    }

    private Subscription manualReviewSubscription() {
        Subscription subscription =
                mock(Subscription.class);

        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.MANUAL_REVIEW);

        when(subscription.isQuantityReserved())
                .thenReturn(true);

        return subscription;
    }

    private WalletHoldStatusResponse walletStatus(
            UUID subscriptionId,
            WalletHoldStatus status
    ) {
        return new WalletHoldStatusResponse(
                subscriptionId,
                100_000L,
                status,
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