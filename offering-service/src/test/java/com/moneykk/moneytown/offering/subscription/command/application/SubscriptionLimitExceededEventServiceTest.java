package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.subscription.domain.entity.IdempotencyOperation;
import com.moneykk.moneytown.offering.subscription.domain.repository.IdempotencyRequestRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionLimitExceededEventServiceTest {

    @Mock
    private IdempotencyRequestRepository idempotencyRequestRepository;

    @Mock
    private SubscriptionEventPublisher subscriptionEventPublisher;

    @InjectMocks
    private SubscriptionLimitExceededEventService
            subscriptionLimitExceededEventService;

    @Test
    @DisplayName("멱등 요청을 실패 처리한 뒤 한도 초과 이벤트를 저장한다")
    void recordsIdempotencyFailureAndLimitExceededEvent() {
        UUID idempotencyRequestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        String idempotencyKey = "subscription-limit-test-key";
        String correlationId = UUID.randomUUID().toString();

        long requestedQuantity = 101L;
        long maxSubscriptionQuantity = 100L;

        when(idempotencyRequestRepository.fail(
                userId,
                IdempotencyOperation.CREATE_SUBSCRIPTION.name(),
                idempotencyKey,
                SubscriptionErrorCode.SUBSCRIPTION_LIMIT_EXCEEDED
                        .getStatus()
                        .value()
        )).thenReturn(1);

        subscriptionLimitExceededEventService.recordLimitExceeded(
                idempotencyRequestId,
                userId,
                idempotencyKey,
                assetId,
                requestedQuantity,
                maxSubscriptionQuantity,
                correlationId
        );

        /*
         * 멱등 실패 처리가 성공한 뒤
         * Outbox 이벤트가 저장되는 순서를 검증한다.
         */
        InOrder inOrder = inOrder(
                idempotencyRequestRepository,
                subscriptionEventPublisher
        );

        inOrder.verify(idempotencyRequestRepository).fail(
                userId,
                IdempotencyOperation.CREATE_SUBSCRIPTION.name(),
                idempotencyKey,
                SubscriptionErrorCode.SUBSCRIPTION_LIMIT_EXCEEDED
                        .getStatus()
                        .value()
        );

        inOrder.verify(subscriptionEventPublisher).publishLimitExceeded(
                idempotencyRequestId,
                userId,
                assetId,
                requestedQuantity,
                maxSubscriptionQuantity,
                correlationId
        );
    }

    @Test
    @DisplayName("멱등 요청 실패 처리에 실패하면 한도 초과 이벤트를 저장하지 않는다")
    void doesNotPublishEventWhenIdempotencyFailureUpdateFails() {
        UUID idempotencyRequestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        String idempotencyKey = "subscription-limit-test-key";
        String correlationId = UUID.randomUUID().toString();

        long requestedQuantity = 101L;
        long maxSubscriptionQuantity = 100L;

        when(idempotencyRequestRepository.fail(
                userId,
                IdempotencyOperation.CREATE_SUBSCRIPTION.name(),
                idempotencyKey,
                SubscriptionErrorCode.SUBSCRIPTION_LIMIT_EXCEEDED
                        .getStatus()
                        .value()
        )).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subscriptionLimitExceededEventService
                        .recordLimitExceeded(
                                idempotencyRequestId,
                                userId,
                                idempotencyKey,
                                assetId,
                                requestedQuantity,
                                maxSubscriptionQuantity,
                                correlationId
                        )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(
                        SubscriptionErrorCode
                                .IDEMPOTENCY_REQUEST_STATE_INVALID
                );

        verifyNoInteractions(subscriptionEventPublisher);
    }
}