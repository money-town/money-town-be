package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.IdempotencyRequestRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

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

    @Mock
    private SubscriptionEventPublisher subscriptionEventPublisher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                subscriptionTransactionService,
                "reservationTimeoutMinutes",
                10L
        );
    }

    @Test
    @DisplayName("선착순 수량 확보에 실패하면 잔여 수량 부족 예외가 발생하고 청약을 생성하지 않는다")
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

        // when
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> subscriptionTransactionService.createSubscription(
                                offeringId,
                                userId,
                                idempotencyKey,
                                quantity,
                                pricePerUnit,
                                "test-correlation-id"
                        )
                );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        SubscriptionErrorCode.INSUFFICIENT_REMAINING_QUANTITY
                );

        // 수량 확보에 실패하면 Outbox 이벤트도 저장하지 않는다.
        verify(subscriptionEventPublisher, never())
                .publishReserved(any(), any());

        // 중복 청약 검증을 정상적으로 통과했는지 확인
        verify(subscriptionRepository)
                .existsByOfferingIdAndUserIdAndIsDeletedFalse(
                        offeringId,
                        userId
                );

        // 선착순 수량 확보를 실제로 시도했는지 확인
        verify(offeringRepository)
                .reserveQuantity(
                        offeringId,
                        quantity,
                        userId
                );

        // 수량 확보 실패 이후 Subscription은 생성하지 않는다.
        verify(subscriptionRepository, never())
                .saveAndFlush(any());

        // 청약 생성에 실패했으므로 멱등 요청 완료 처리도 하지 않는다.
        verify(idempotencyRequestRepository, never())
                .complete(
                        any(),
                        any(),
                        any(),
                        any(),
                        anyInt()
                );
    }

    @Test
    @DisplayName("동시 중복 청약의 UNIQUE 제약 위반은 중복 청약 오류로 변환한다")
    void translatesDuplicateSubscriptionConstraintViolation() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String idempotencyKey = "duplicate-key";
        Long quantity = 10L;
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
                .thenReturn(1);

        when(subscriptionRepository.saveAndFlush(any()))
                .thenThrow(constraintViolation(
                        "uq_subscriptions_offering_user"
                ));

        // when
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> subscriptionTransactionService
                                .createSubscription(
                                        offeringId,
                                        userId,
                                        idempotencyKey,
                                        quantity,
                                        pricePerUnit,
                                        "test-correlation-id"
                                )
                );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        SubscriptionErrorCode.DUPLICATE_SUBSCRIPTION
                );

        verify(subscriptionEventPublisher, never())
                .publishReserved(any(), any());

        verify(idempotencyRequestRepository, never())
                .complete(
                        any(),
                        any(),
                        any(),
                        any(),
                        anyInt()
                );
    }

    @Test
    @DisplayName("중복 청약이 아닌 DB 제약 위반은 원래 예외를 전달한다")
    void propagatesUnrecognizedConstraintViolation() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String idempotencyKey = "constraint-key";
        Long quantity = 10L;
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
                .thenReturn(1);

        DataIntegrityViolationException exception =
                constraintViolation("some_other_constraint");

        when(subscriptionRepository.saveAndFlush(any()))
                .thenThrow(exception);

        // when & then
        assertThatThrownBy(
                () -> subscriptionTransactionService
                        .createSubscription(
                                offeringId,
                                userId,
                                idempotencyKey,
                                quantity,
                                pricePerUnit,
                                "test-correlation-id"
                        )
        )
                .isSameAs(exception);

        verify(subscriptionEventPublisher, never())
                .publishReserved(any(), any());

        verify(idempotencyRequestRepository, never())
                .complete(
                        any(),
                        any(),
                        any(),
                        any(),
                        anyInt()
                );
    }

    private DataIntegrityViolationException constraintViolation(
            String constraintName
    ) {
        ConstraintViolationException cause =
                new ConstraintViolationException(
                        "duplicate key value violates unique constraint",
                        new SQLException("duplicate key"),
                        constraintName
                );

        return new DataIntegrityViolationException(
                "constraint violation",
                cause
        );
    }
}