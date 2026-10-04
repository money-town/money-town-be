package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionRequest;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionRequestStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.IdempotencyRequestRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRequestRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionAsyncTransactionServiceTest {

    @Mock SubscriptionRepository subscriptionRepository;
    @Mock OfferingRepository offeringRepository;
    @Mock IdempotencyRequestRepository idempotencyRequestRepository;
    @Mock SubscriptionRequestRepository subscriptionRequestRepository;
    @Mock SubscriptionEventPublisher eventPublisher;
    @Mock OfferingQuantityReservationService quantityReservationService;
    @InjectMocks SubscriptionTransactionService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "reservationTimeoutMinutes", 10L);
    }

    @Test
    void reservesAndCompletesRequestInAsyncTransaction() {
        UUID requestId = UUID.randomUUID();
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SubscriptionRequest request = SubscriptionRequest.create(
                requestId, offeringId, userId, 1L,
                "key", "a".repeat(64), "correlation"
        );
        request.startProcessing(Instant.now());

        when(subscriptionRepository
                .existsByOfferingIdAndUserIdAndIsDeletedFalse(offeringId, userId))
                .thenReturn(false);
        when(offeringRepository.reserveQuantity(offeringId, 1L, userId))
                .thenReturn(1);
        when(subscriptionRepository.saveAndFlush(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionRequestRepository.findByIdForUpdate(requestId))
                .thenReturn(Optional.of(request));

        var response = service.createSubscriptionForRequest(
                requestId, offeringId, userId,
                1L, 1000L, "correlation"
        );

        assertThat(response.offeringId()).isEqualTo(offeringId);
        assertThat(request.getRequestStatus())
                .isEqualTo(SubscriptionRequestStatus.COMPLETED);
        assertThat(request.getSubscriptionId()).isEqualTo(response.subscriptionId());
        verify(eventPublisher).publishReserved(any(Subscription.class),
                org.mockito.ArgumentMatchers.eq("correlation"));
    }
}
