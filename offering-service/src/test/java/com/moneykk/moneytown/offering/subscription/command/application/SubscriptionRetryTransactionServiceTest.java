package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionRetryResponse;
import com.moneykk.moneytown.offering.subscription.domain.entity.HoldingAllocationStatus;
import com.moneykk.moneytown.offering.subscription.domain.entity.IdempotencyOperation;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.IdempotencyRequestRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.HoldingSubscriptionStatusResponse;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.WalletHoldStatus;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.dto.WalletHoldStatusResponse;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionRetryTransactionServiceTest {

    @Mock
    private OfferingRepository offeringRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private IdempotencyRequestRepository
            idempotencyRequestRepository;

    @Mock
    private SubscriptionEventPublisher
            subscriptionEventPublisher;

    @InjectMocks
    private SubscriptionRetryTransactionService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                service,
                "reservationTimeoutMinutes",
                10L
        );
    }

    @Test
    @DisplayName("Wallet Hold가 없으면 PROCESSING으로 복구하고 동결 요청을 재발행한다")
    void retriesWalletHoldWhenHoldDoesNotExist() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "retry-key";
        String correlationId = "correlation-id";

        Offering offering = createOffering(
                offeringId,
                OfferingStatus.OPEN,
                90L
        );

        Subscription subscription =
                createManualReviewSubscription(
                        offeringId,
                        subscriptionId
                );

        Instant previousExpiresAt =
                subscription.getReservationExpiresAt();

        HoldingSubscriptionStatusResponse holdingStatus =
                holdingStatusWithoutAllocation(subscriptionId);

        stubLockedEntities(
                offering,
                subscription,
                subscriptionId
        );

        stubIdempotencyCompletion(
                subscriptionId,
                adminId,
                idempotencyKey
        );

        // when
        SubscriptionRetryResponse response =
                service.retry(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        correlationId,
                        null,
                        holdingStatus
                );

        // then
        assertThat(response.subscriptionStatus())
                .isEqualTo(SubscriptionStatus.PROCESSING);

        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.PROCESSING);

        assertThat(subscription.getReservationExpiresAt())
                .isAfter(previousExpiresAt);

        assertThat(subscription.getFailureCode()).isNull();

        verify(subscriptionEventPublisher)
                .publishReserved(
                        subscription,
                        correlationId
                );

        verify(subscriptionEventPublisher, never())
                .publishConfirmed(
                        any(Subscription.class),
                        any(UUID.class),
                        anyString()
                );

        verifyIdempotencyCompletion(
                subscriptionId,
                adminId,
                idempotencyKey
        );
    }

    @Test
    @DisplayName("Wallet HELD가 확인되면 청약을 HOLD_SUCCEEDED로 복구한다")
    void recoversHeldSubscription() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "held-retry-key";
        String correlationId = "correlation-id";

        Offering offering = createOffering(
                offeringId,
                OfferingStatus.OPEN,
                90L
        );

        Subscription subscription =
                createManualReviewSubscription(
                        offeringId,
                        subscriptionId
                );

        WalletHoldStatusResponse walletStatus =
                walletStatus(
                        subscription,
                        WalletHoldStatus.HELD
                );

        HoldingSubscriptionStatusResponse holdingStatus =
                holdingStatusWithoutAllocation(subscriptionId);

        stubLockedEntities(
                offering,
                subscription,
                subscriptionId
        );

        stubIdempotencyCompletion(
                subscriptionId,
                adminId,
                idempotencyKey
        );

        // when
        SubscriptionRetryResponse response =
                service.retry(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        correlationId,
                        walletStatus,
                        holdingStatus
                );

        // then
        assertThat(response.subscriptionStatus())
                .isEqualTo(SubscriptionStatus.HOLD_SUCCEEDED);

        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.HOLD_SUCCEEDED);

        assertThat(subscription.getReservationExpiresAt()).isNull();
        assertThat(subscription.getFailureCode()).isNull();

        verify(subscriptionEventPublisher, never())
                .publishReserved(
                        any(Subscription.class),
                        anyString()
                );

        verify(subscriptionEventPublisher, never())
                .publishConfirmed(
                        any(Subscription.class),
                        any(UUID.class),
                        anyString()
                );

        verifyIdempotencyCompletion(
                subscriptionId,
                adminId,
                idempotencyKey
        );
    }

    @Test
    @DisplayName("매진 공모의 모든 Wallet Hold가 성공하면 청약들을 일괄 확정한다")
    void confirmsAllSubscriptionsWhenEveryHoldSucceeded() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID otherSubscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "confirm-all-key";
        String correlationId = "correlation-id";

        Offering offering = createOffering(
                offeringId,
                OfferingStatus.SOLD_OUT,
                0L
        );

        Subscription subscription =
                createManualReviewSubscription(
                        offeringId,
                        subscriptionId
                );

        Subscription otherSubscription =
                createSubscription(
                        offeringId,
                        otherSubscriptionId
                );

        otherSubscription.markHoldSucceeded();

        WalletHoldStatusResponse walletStatus =
                walletStatus(
                        subscription,
                        WalletHoldStatus.HELD
                );

        HoldingSubscriptionStatusResponse holdingStatus =
                holdingStatusWithoutAllocation(subscriptionId);

        stubLockedEntities(
                offering,
                subscription,
                subscriptionId
        );

        when(subscriptionRepository
                .findAllReservedByOfferingIdForUpdate(
                        offeringId
                ))
                .thenReturn(
                        List.of(
                                subscription,
                                otherSubscription
                        )
                );

        stubIdempotencyCompletion(
                subscriptionId,
                adminId,
                idempotencyKey
        );

        // when
        SubscriptionRetryResponse response =
                service.retry(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        correlationId,
                        walletStatus,
                        holdingStatus
                );

        // then
        assertThat(response.subscriptionStatus())
                .isEqualTo(SubscriptionStatus.CONFIRMED);

        assertThat(subscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.CONFIRMED);

        assertThat(otherSubscription.getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.CONFIRMED);

        verify(subscriptionEventPublisher)
                .publishConfirmed(
                        subscription,
                        offering.getAssetId(),
                        correlationId
                );

        verify(subscriptionEventPublisher)
                .publishConfirmed(
                        otherSubscription,
                        offering.getAssetId(),
                        correlationId
                );

        verifyIdempotencyCompletion(
                subscriptionId,
                adminId,
                idempotencyKey
        );
    }

    @Test
    @DisplayName("Wallet COMMITTED이고 Holding 배정이 없으면 확정 이벤트를 재발행한다")
    void republishesConfirmedEventWhenHoldingIsMissing() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "committed-retry-key";
        String correlationId = "correlation-id";

        Offering offering = createOffering(
                offeringId,
                OfferingStatus.CLOSED,
                0L
        );

        Subscription subscription =
                createConfirmedManualReviewSubscription(
                        offeringId,
                        subscriptionId
                );

        WalletHoldStatusResponse walletStatus =
                walletStatus(
                        subscription,
                        WalletHoldStatus.COMMITTED
                );

        HoldingSubscriptionStatusResponse holdingStatus =
                holdingStatusWithoutAllocation(subscriptionId);

        stubLockedEntities(
                offering,
                subscription,
                subscriptionId
        );

        stubIdempotencyCompletion(
                subscriptionId,
                adminId,
                idempotencyKey
        );

        // when
        SubscriptionRetryResponse response =
                service.retry(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        correlationId,
                        walletStatus,
                        holdingStatus
                );

        // then
        assertThat(response.subscriptionStatus())
                .isEqualTo(SubscriptionStatus.CONFIRMED);

        assertThat(subscription.getHoldingAllocationStatus())
                .isEqualTo(HoldingAllocationStatus.PENDING);

        assertThat(subscription.getHoldingAllocationErrorCode())
                .isNull();

        verify(subscriptionEventPublisher)
                .publishConfirmed(
                        subscription,
                        offering.getAssetId(),
                        correlationId
                );

        verifyIdempotencyCompletion(
                subscriptionId,
                adminId,
                idempotencyKey
        );
    }

    @Test
    @DisplayName("Wallet과 Holding 처리가 모두 완료됐으면 이벤트 없이 성공 상태를 복구한다")
    void reconcilesCompletedHoldingWithoutPublishingEvent() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "completed-holding-key";
        String correlationId = "correlation-id";

        Offering offering = createOffering(
                offeringId,
                OfferingStatus.CLOSED,
                0L
        );

        Subscription subscription =
                createConfirmedManualReviewSubscription(
                        offeringId,
                        subscriptionId
                );

        WalletHoldStatusResponse walletStatus =
                walletStatus(
                        subscription,
                        WalletHoldStatus.COMMITTED
                );

        HoldingSubscriptionStatusResponse holdingStatus =
                holdingStatusWithAllocation(
                        subscription,
                        offering.getAssetId()
                );

        stubLockedEntities(
                offering,
                subscription,
                subscriptionId
        );

        stubIdempotencyCompletion(
                subscriptionId,
                adminId,
                idempotencyKey
        );

        // when
        SubscriptionRetryResponse response =
                service.retry(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        correlationId,
                        walletStatus,
                        holdingStatus
                );

        // then
        assertThat(response.subscriptionStatus())
                .isEqualTo(SubscriptionStatus.CONFIRMED);

        assertThat(subscription.getHoldingAllocationStatus())
                .isEqualTo(HoldingAllocationStatus.SUCCEEDED);

        assertThat(subscription.getHoldingAllocationErrorCode())
                .isNull();

        verify(subscriptionEventPublisher, never())
                .publishConfirmed(
                        any(Subscription.class),
                        any(UUID.class),
                        anyString()
                );

        verify(subscriptionEventPublisher, never())
                .publishReserved(
                        any(Subscription.class),
                        anyString()
                );

        verifyIdempotencyCompletion(
                subscriptionId,
                adminId,
                idempotencyKey
        );
    }

    @Test
    @DisplayName("CONFIRMED 상태의 Holding 배정 실패는 확정 이벤트를 재발행한다")
    void retriesConfirmedHoldingFailure() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "holding-failure-key";
        String correlationId = "correlation-id";

        Offering offering = createOffering(
                offeringId,
                OfferingStatus.CLOSED,
                0L
        );

        Subscription subscription =
                createConfirmedSubscription(
                        offeringId,
                        subscriptionId
                );

        subscription.markHoldingAllocationFailed(
                "HOLDING_ALLOCATION_FAILED"
        );

        WalletHoldStatusResponse walletStatus =
                walletStatus(
                        subscription,
                        WalletHoldStatus.COMMITTED
                );

        HoldingSubscriptionStatusResponse holdingStatus =
                holdingStatusWithoutAllocation(subscriptionId);

        stubLockedEntities(
                offering,
                subscription,
                subscriptionId
        );

        stubIdempotencyCompletion(
                subscriptionId,
                adminId,
                idempotencyKey
        );

        // when
        SubscriptionRetryResponse response =
                service.retry(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        correlationId,
                        walletStatus,
                        holdingStatus
                );

        // then
        assertThat(response.subscriptionStatus())
                .isEqualTo(SubscriptionStatus.CONFIRMED);

        assertThat(subscription.getHoldingAllocationStatus())
                .isEqualTo(HoldingAllocationStatus.PENDING);

        assertThat(subscription.getHoldingAllocationErrorCode())
                .isNull();

        verify(subscriptionEventPublisher)
                .publishConfirmed(
                        subscription,
                        offering.getAssetId(),
                        correlationId
                );
    }

    @Test
    @DisplayName("Wallet이 RELEASED 상태이면 정상 재처리를 거부한다")
    void rejectsReleasedWalletHold() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        Offering offering = createOffering(
                offeringId,
                OfferingStatus.OPEN,
                90L
        );

        Subscription subscription =
                createManualReviewSubscription(
                        offeringId,
                        subscriptionId
                );

        WalletHoldStatusResponse walletStatus =
                walletStatus(
                        subscription,
                        WalletHoldStatus.RELEASED
                );

        HoldingSubscriptionStatusResponse holdingStatus =
                holdingStatusWithoutAllocation(subscriptionId);

        stubLockedEntities(
                offering,
                subscription,
                subscriptionId
        );

        // when & then
        assertThatThrownBy(() ->
                service.retry(
                        subscriptionId,
                        adminId,
                        "released-key",
                        "correlation-id",
                        walletStatus,
                        holdingStatus
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SubscriptionErrorCode
                                        .SUBSCRIPTION_RETRY_NOT_ALLOWED
                        )
                );

        verify(subscriptionEventPublisher, never())
                .publishReserved(
                        any(Subscription.class),
                        anyString()
                );

        verify(subscriptionEventPublisher, never())
                .publishConfirmed(
                        any(Subscription.class),
                        any(UUID.class),
                        anyString()
                );
    }

    @Test
    @DisplayName("Holding 응답의 청약 ID가 다르면 외부 응답 오류로 처리한다")
    void rejectsMismatchedHoldingResponse() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        Offering offering = createOffering(
                offeringId,
                OfferingStatus.OPEN,
                90L
        );

        Subscription subscription =
                createManualReviewSubscription(
                        offeringId,
                        subscriptionId
                );

        HoldingSubscriptionStatusResponse holdingStatus =
                holdingStatusWithoutAllocation(
                        UUID.randomUUID()
                );

        stubLockedEntities(
                offering,
                subscription,
                subscriptionId
        );

        // when & then
        assertThatThrownBy(() ->
                service.retry(
                        subscriptionId,
                        UUID.randomUUID(),
                        "invalid-holding-key",
                        "correlation-id",
                        null,
                        holdingStatus
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SubscriptionErrorCode
                                        .EXTERNAL_RESPONSE_INVALID
                        )
                );
    }

    @Test
    @DisplayName("멱등 요청 완료 건수가 1건이 아니면 재처리를 실패한다")
    void failsWhenIdempotencyCompletionDoesNotUpdate() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "idempotency-failure-key";

        Offering offering = createOffering(
                offeringId,
                OfferingStatus.OPEN,
                90L
        );

        Subscription subscription =
                createManualReviewSubscription(
                        offeringId,
                        subscriptionId
                );

        HoldingSubscriptionStatusResponse holdingStatus =
                holdingStatusWithoutAllocation(subscriptionId);

        stubLockedEntities(
                offering,
                subscription,
                subscriptionId
        );

        when(idempotencyRequestRepository.complete(
                adminId,
                IdempotencyOperation.RETRY_SUBSCRIPTION.name(),
                idempotencyKey,
                subscriptionId,
                202
        )).thenReturn(0);

        // when & then
        assertThatThrownBy(() ->
                service.retry(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        "correlation-id",
                        null,
                        holdingStatus
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SubscriptionErrorCode
                                        .IDEMPOTENCY_COMPLETION_FAILED
                        )
                );
    }

    private void stubLockedEntities(
            Offering offering,
            Subscription subscription,
            UUID subscriptionId
    ) {
        when(subscriptionRepository
                .findOfferingIdBySubscriptionId(
                        subscriptionId
                ))
                .thenReturn(
                        Optional.of(
                                offering.getOfferingId()
                        )
                );

        when(offeringRepository.findByIdForUpdate(
                offering.getOfferingId()
        )).thenReturn(Optional.of(offering));

        when(subscriptionRepository.findByIdForUpdate(
                subscriptionId
        )).thenReturn(Optional.of(subscription));
    }

    private void stubIdempotencyCompletion(
            UUID subscriptionId,
            UUID adminId,
            String idempotencyKey
    ) {
        when(idempotencyRequestRepository.complete(
                adminId,
                IdempotencyOperation.RETRY_SUBSCRIPTION.name(),
                idempotencyKey,
                subscriptionId,
                202
        )).thenReturn(1);
    }

    private void verifyIdempotencyCompletion(
            UUID subscriptionId,
            UUID adminId,
            String idempotencyKey
    ) {
        verify(idempotencyRequestRepository)
                .complete(
                        adminId,
                        IdempotencyOperation.RETRY_SUBSCRIPTION.name(),
                        idempotencyKey,
                        subscriptionId,
                        202
                );
    }

    private Offering createOffering(
            UUID offeringId,
            OfferingStatus offeringStatus,
            Long remainingQuantity
    ) {
        Offering offering = Offering.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "재처리 테스트 공모",
                10_000L,
                100L,
                1L,
                100L,
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200)
        );

        ReflectionTestUtils.setField(
                offering,
                "offeringId",
                offeringId
        );

        ReflectionTestUtils.setField(
                offering,
                "offeringStatus",
                offeringStatus
        );

        ReflectionTestUtils.setField(
                offering,
                "remainingQuantity",
                remainingQuantity
        );

        return offering;
    }

    private Subscription createSubscription(
            UUID offeringId,
            UUID subscriptionId
    ) {
        Subscription subscription = Subscription.create(
                offeringId,
                UUID.randomUUID(),
                10L,
                10_000L,
                Instant.now().plusSeconds(300)
        );

        ReflectionTestUtils.setField(
                subscription,
                "subscriptionId",
                subscriptionId
        );

        return subscription;
    }

    private Subscription createManualReviewSubscription(
            UUID offeringId,
            UUID subscriptionId
    ) {
        Subscription subscription =
                createSubscription(
                        offeringId,
                        subscriptionId
                );

        subscription.requireManualReview(
                "RETRY_REQUIRED"
        );

        return subscription;
    }

    private Subscription createConfirmedSubscription(
            UUID offeringId,
            UUID subscriptionId
    ) {
        Subscription subscription =
                createSubscription(
                        offeringId,
                        subscriptionId
                );

        subscription.markHoldSucceeded();
        subscription.confirm(Instant.now());

        return subscription;
    }

    private Subscription createConfirmedManualReviewSubscription(
            UUID offeringId,
            UUID subscriptionId
    ) {
        Subscription subscription =
                createConfirmedSubscription(
                        offeringId,
                        subscriptionId
                );

        subscription.markHoldingAllocationFailed(
                "HOLDING_ALLOCATION_FAILED"
        );

        subscription.requireManualReview(
                "CONFIRMED_PROCESSING_FAILED"
        );

        return subscription;
    }

    private WalletHoldStatusResponse walletStatus(
            Subscription subscription,
            WalletHoldStatus status
    ) {
        return new WalletHoldStatusResponse(
                subscription.getSubscriptionId(),
                subscription.getAmount(),
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

    private HoldingSubscriptionStatusResponse
    holdingStatusWithAllocation(
            Subscription subscription,
            UUID assetId
    ) {
        return new HoldingSubscriptionStatusResponse(
                subscription.getSubscriptionId(),
                UUID.randomUUID(),
                assetId,
                subscription.getUserId(),
                subscription.getQuantity(),
                0L,
                true,
                false,
                false,
                Instant.now()
        );
    }

    @Test
    @DisplayName("SOLD_OUT 공모에서도 Wallet Hold가 없으면 동결 요청을 재발행한다")
    void retriesMissingWalletHoldForSoldOutOffering() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        String idempotencyKey = "sold-out-retry-key";
        String correlationId = "correlation-id";

        Offering offering = createOffering(
                offeringId,
                OfferingStatus.SOLD_OUT,
                0L
        );

        Subscription subscription =
                createManualReviewSubscription(
                        offeringId,
                        subscriptionId
                );

        HoldingSubscriptionStatusResponse holdingStatus =
                holdingStatusWithoutAllocation(subscriptionId);

        stubLockedEntities(
                offering,
                subscription,
                subscriptionId
        );

        stubIdempotencyCompletion(
                subscriptionId,
                adminId,
                idempotencyKey
        );

        // when
        SubscriptionRetryResponse response =
                service.retry(
                        subscriptionId,
                        adminId,
                        idempotencyKey,
                        correlationId,
                        null,
                        holdingStatus
                );

        // then
        assertThat(response.subscriptionStatus())
                .isEqualTo(SubscriptionStatus.PROCESSING);

        verify(subscriptionEventPublisher)
                .publishReserved(
                        subscription,
                        correlationId
                );

        verifyIdempotencyCompletion(
                subscriptionId,
                adminId,
                idempotencyKey
        );
    }
}