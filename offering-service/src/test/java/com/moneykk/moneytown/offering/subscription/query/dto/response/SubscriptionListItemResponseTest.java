package com.moneykk.moneytown.offering.subscription.query.dto.response;

import com.moneykk.moneytown.offering.subscription.domain.entity.CancellationType;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubscriptionListItemResponseTest {

    @Test
    @DisplayName("Subscription 엔티티의 필드를 목록 응답 필드에 그대로 매핑한다")
    void mapsSubscriptionFieldsToResponse() {
        // given
        UUID subscriptionId = UUID.randomUUID();
        UUID offeringId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-10T09:05:00Z");
        Instant updatedAt = Instant.parse("2026-09-10T09:06:00Z");

        Subscription subscription = mock(Subscription.class);
        when(subscription.getSubscriptionId()).thenReturn(subscriptionId);
        when(subscription.getOfferingId()).thenReturn(offeringId);
        when(subscription.getQuantity()).thenReturn(10L);
        when(subscription.getPricePerUnit()).thenReturn(100_000L);
        when(subscription.getAmount()).thenReturn(1_000_000L);
        when(subscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.CANCELLED);
        when(subscription.getWalletHoldFailureCode())
                .thenReturn("INSUFFICIENT_AVAILABLE_BALANCE");
        when(subscription.getSubscriptionFailureCode())
                .thenReturn("RESERVATION_EXPIRED");
        when(subscription.getCancellationType())
                .thenReturn(CancellationType.OFFERING_ADMIN_CANCELLED);
        when(subscription.getCreatedAt()).thenReturn(createdAt);
        when(subscription.getUpdatedAt()).thenReturn(updatedAt);

        // when
        SubscriptionListItemResponse response =
                SubscriptionListItemResponse.from(subscription);

        // then
        assertThat(response.subscriptionId()).isEqualTo(subscriptionId);
        assertThat(response.offeringId()).isEqualTo(offeringId);
        assertThat(response.quantity()).isEqualTo(10L);
        assertThat(response.pricePerUnit()).isEqualTo(100_000L);
        assertThat(response.amount()).isEqualTo(1_000_000L);
        assertThat(response.subscriptionStatus())
                .isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(response.walletHoldFailureCode())
                .isEqualTo("INSUFFICIENT_AVAILABLE_BALANCE");
        assertThat(response.subscriptionFailureCode())
                .isEqualTo("RESERVATION_EXPIRED");
        assertThat(response.cancellationType())
                .isEqualTo(CancellationType.OFFERING_ADMIN_CANCELLED);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
    }
}
