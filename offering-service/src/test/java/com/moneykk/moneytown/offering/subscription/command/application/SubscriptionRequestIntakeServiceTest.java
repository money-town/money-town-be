package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.subscription.command.dto.request.SubscriptionCreateRequest;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionRequest;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRequestRepository;
import com.moneykk.moneytown.offering.subscription.domain.service.SubscriptionRequestHasher;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import com.moneykk.moneytown.offering.subscription.monitoring.SubscriptionRequestMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionRequestIntakeServiceTest {

    @Mock SubscriptionRequestRepository requestRepository;
    @Mock SubscriptionRequestHasher requestHasher;
    @Mock SubscriptionEventPublisher eventPublisher;
    @Mock SubscriptionRequestMetrics metrics;
    @InjectMocks SubscriptionRequestIntakeService service;

    @Test
    void storesRequestAndOutboxForNewIdempotencyKey() {
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String hash = "a".repeat(64);
        when(requestHasher.hash(offeringId, 1L)).thenReturn(hash);
        when(requestRepository.insertIfAbsent(
                any(), eq(offeringId), eq(userId), eq(1L),
                eq("key"), eq(hash), eq("correlation")
        )).thenReturn(1);
        var result = service.accept(
                offeringId, userId, "key",
                new SubscriptionCreateRequest(1L), "correlation"
        );

        assertThat(result.replayed()).isFalse();
        assertThat(result.response().requestId())
                .isNotNull();
        verify(eventPublisher).publishRequested(any(SubscriptionRequest.class));
        verify(metrics).recordAccepted();
    }

    @Test
    void returnsExistingRequestWithoutPublishingDuplicateOutbox() {
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String hash = "b".repeat(64);
        SubscriptionRequest stored = SubscriptionRequest.create(
                UUID.randomUUID(), offeringId, userId, 1L,
                "key", hash, "correlation"
        );

        when(requestHasher.hash(offeringId, 1L)).thenReturn(hash);
        when(requestRepository.insertIfAbsent(
                any(), eq(offeringId), eq(userId), eq(1L),
                eq("key"), eq(hash), eq("correlation")
        )).thenReturn(0);
        when(requestRepository.findByUserIdAndIdempotencyKey(userId, "key"))
                .thenReturn(Optional.of(stored));

        var result = service.accept(
                offeringId, userId, "key",
                new SubscriptionCreateRequest(1L), "correlation"
        );

        assertThat(result.replayed()).isTrue();
        verify(eventPublisher, never()).publishRequested(any());
        verify(metrics).recordReplayed();
    }

    @Test
    void rejectsSameKeyWithDifferentRequestHash() {
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SubscriptionRequest stored = SubscriptionRequest.create(
                UUID.randomUUID(), offeringId, userId, 1L,
                "key", "a".repeat(64), "correlation"
        );

        when(requestHasher.hash(offeringId, 2L)).thenReturn("b".repeat(64));
        when(requestRepository.insertIfAbsent(any(), eq(offeringId), eq(userId),
                eq(2L), eq("key"), any(), eq("correlation")))
                .thenReturn(0);
        when(requestRepository.findByUserIdAndIdempotencyKey(userId, "key"))
                .thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.accept(
                offeringId, userId, "key",
                new SubscriptionCreateRequest(2L), "correlation"
        )).isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(SubscriptionErrorCode.IDEMPOTENCY_KEY_CONFLICT));
    }

    @Test
    void rejectsNonPositiveQuantityBeforeWritingDatabase() {
        assertThatThrownBy(() -> service.accept(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "key",
                new SubscriptionCreateRequest(0L),
                "correlation"
        )).isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(
                                SubscriptionErrorCode.INVALID_SUBSCRIPTION_QUANTITY
                        ));

        verify(requestRepository, never()).insertIfAbsent(
                any(), any(), any(), any(), any(), any(), any()
        );
        verify(eventPublisher, never()).publishRequested(any());
    }
}
