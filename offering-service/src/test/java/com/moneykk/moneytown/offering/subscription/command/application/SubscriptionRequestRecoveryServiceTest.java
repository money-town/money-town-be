package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionRequest;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionRequestStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRequestRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import com.moneykk.moneytown.offering.subscription.monitoring.SubscriptionRequestMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionRequestRecoveryServiceTest {

    @Mock SubscriptionRequestRepository repository;
    @Mock SubscriptionEventPublisher eventPublisher;
    @Mock SubscriptionRequestMetrics metrics;
    @InjectMocks SubscriptionRequestRecoveryService service;

    @Test
    void requeuesStuckRequestAndStoresNewOutboxEvent() {
        SubscriptionRequest request = SubscriptionRequest.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                1L, "key", "a".repeat(64), "correlation"
        );
        request.startProcessing(Instant.now().minusSeconds(600));
        ReflectionTestUtils.setField(service, "stuckSeconds", 300L);
        ReflectionTestUtils.setField(service, "batchSize", 100);
        when(repository.findStuckProcessingForUpdate(any(), eq(100)))
                .thenReturn(List.of(request));

        int recovered = service.recoverStuckRequests();

        assertThat(recovered).isEqualTo(1);
        assertThat(request.getRequestStatus())
                .isEqualTo(SubscriptionRequestStatus.QUEUED);
        verify(eventPublisher).publishRequested(request);
        verify(metrics).recordRecovered(1);
    }
}
