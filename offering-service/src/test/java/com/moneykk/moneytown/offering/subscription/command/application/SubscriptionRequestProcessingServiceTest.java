package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.subscription.monitoring.SubscriptionRequestMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionRequestProcessingServiceTest {

    @Mock SubscriptionRequestStateService stateService;
    @Mock SubscriptionCommandService commandService;
    @Mock SubscriptionRequestMetrics metrics;
    @InjectMocks SubscriptionRequestProcessingService service;

    @Test
    void acknowledgesAlreadyTerminalRequestWithoutExecutingAgain() {
        UUID requestId = UUID.randomUUID();
        when(stateService.start(requestId)).thenReturn(Optional.empty());

        service.process(requestId);

        verify(commandService, never()).processAcceptedRequest(
                any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void marksBusinessFailureAsRejected() {
        var work = work();
        when(stateService.start(work.requestId()))
                .thenReturn(Optional.of(work));
        when(commandService.processAcceptedRequest(
                work.requestId(), work.offeringId(), work.userId(),
                work.idempotencyKey(), work.quantity(), work.correlationId()
        )).thenThrow(new BusinessException(
                SubscriptionErrorCode.INSUFFICIENT_REMAINING_QUANTITY
        ));

        service.process(work.requestId());

        verify(stateService).reject(
                work.requestId(),
                SubscriptionErrorCode.INSUFFICIENT_REMAINING_QUANTITY.getCode()
        );
    }

    @Test
    void propagatesServiceUnavailableForKafkaRetry() {
        var work = work();
        when(stateService.start(work.requestId()))
                .thenReturn(Optional.of(work));
        when(commandService.processAcceptedRequest(
                work.requestId(), work.offeringId(), work.userId(),
                work.idempotencyKey(), work.quantity(), work.correlationId()
        )).thenThrow(new BusinessException(
                SubscriptionErrorCode.USER_SERVICE_UNAVAILABLE
        ));

        assertThatThrownBy(() -> service.process(work.requestId()))
                .isInstanceOf(SubscriptionRequestRetryableException.class);

        verify(stateService, never()).reject(any(), any());
    }

    private SubscriptionRequestStateService.SubscriptionRequestWork work() {
        return new SubscriptionRequestStateService.SubscriptionRequestWork(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                1L, "key", "a".repeat(64), "correlation"
        );
    }
}
