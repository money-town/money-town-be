package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.IdempotencyRequestRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionTransactionServiceTest {

    @Mock
    private OfferingRepository offeringRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private IdempotencyRequestRepository idempotencyRequestRepository;

    @InjectMocks
    private SubscriptionTransactionService subscriptionTransactionService;

    @Test
    @DisplayName("선착순 수량 확보에 실패하면 청약을 생성하지 않는다")
    void createSubscriptionFailsWhenQuantityReservationFails() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String idempotencyKey = "test-key";
        Long quantity = 101L;
        Long pricePerUnit = 10_000L;

        when(subscriptionRepository
                .existsByOfferingIdAndUserIdAndIsDeletedFalse(
                        offeringId,
                        userId
                ))
                .thenReturn(false);

        when(offeringRepository.reserveQuantity(
                offeringId,
                quantity,
                userId
        ))
                .thenReturn(0);

        // when & then
        assertThatThrownBy(() ->
                subscriptionTransactionService.createSubscription(
                        offeringId,
                        userId,
                        idempotencyKey,
                        quantity,
                        pricePerUnit
                )
        )
                .isInstanceOf(BusinessException.class);

        verify(subscriptionRepository, never())
                .save(org.mockito.ArgumentMatchers.any());

        verify(idempotencyRequestRepository, never())
                .complete(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyInt()
                );
    }
}