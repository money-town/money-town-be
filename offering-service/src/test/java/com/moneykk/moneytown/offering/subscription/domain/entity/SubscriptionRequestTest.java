package com.moneykk.moneytown.offering.subscription.domain.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionRequestTest {

    @Test
    void movesThroughQueuedProcessingAndCompleted() {
        SubscriptionRequest request = createRequest();
        UUID subscriptionId = UUID.randomUUID();

        request.startProcessing(Instant.now());
        request.complete(subscriptionId, Instant.now());

        assertThat(request.getRequestStatus())
                .isEqualTo(SubscriptionRequestStatus.COMPLETED);
        assertThat(request.getSubscriptionId()).isEqualTo(subscriptionId);
        assertThat(request.isTerminal()).isTrue();
        assertThat(request.getProcessingStartedAt()).isNull();
    }

    @Test
    void requeuesOnlyProcessingRequest() {
        SubscriptionRequest request = createRequest();

        assertThatThrownBy(request::requeue)
                .isInstanceOf(IllegalStateException.class);

        request.startProcessing(Instant.now());
        request.requeue();

        assertThat(request.getRequestStatus())
                .isEqualTo(SubscriptionRequestStatus.QUEUED);
    }

    @Test
    void rejectsProcessingRequestWithFailureCode() {
        SubscriptionRequest request = createRequest();
        request.startProcessing(Instant.now());

        request.reject("SUBSCRIPTION_409_06", Instant.now());

        assertThat(request.getRequestStatus())
                .isEqualTo(SubscriptionRequestStatus.REJECTED);
        assertThat(request.getFailureCode())
                .isEqualTo("SUBSCRIPTION_409_06");
    }

    @Test
    void requiresProcessingStartTime() {
        assertThatThrownBy(() -> createRequest().startProcessing(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private SubscriptionRequest createRequest() {
        return SubscriptionRequest.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                1L,
                "test-key",
                "a".repeat(64),
                UUID.randomUUID().toString()
        );
    }
}
