package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.IdempotencyRequestRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

import com.moneykk.moneytown.offering.subscription.command.dto.response.SubscriptionCreateResponse;
import com.moneykk.moneytown.offering.subscription.domain.entity.IdempotencyOperation;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;

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

    @Test
    @DisplayName("동일 공모에 이미 청약한 사용자는 수량 확보 전에 중복 청약 오류가 발생한다")
    void rejectsDuplicateSubscriptionBeforeReservingQuantity() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(subscriptionRepository
                .existsByOfferingIdAndUserIdAndIsDeletedFalse(
                        offeringId, userId
                ))
                .thenReturn(true);

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subscriptionTransactionService.createSubscription(
                        offeringId, userId, "dup-key", 10L, 10_000L,
                        "test-correlation-id"
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(SubscriptionErrorCode.DUPLICATE_SUBSCRIPTION);

        verify(offeringRepository, never())
                .reserveQuantity(any(), any(), any());
        verify(subscriptionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("청약 생성에 성공하면 예약 이벤트를 발행하고 멱등 요청을 완료 처리한다")
    void createsSubscriptionSuccessfully() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        String idempotencyKey = "success-key";
        String correlationId = "correlation-id";

        when(subscriptionRepository
                .existsByOfferingIdAndUserIdAndIsDeletedFalse(
                        offeringId, userId
                ))
                .thenReturn(false);

        when(offeringRepository.reserveQuantity(
                offeringId, 10L, userId
        )).thenReturn(1);

        var savedSubscription =
                mock(com.moneykk.moneytown.offering.subscription.domain.entity.Subscription.class);
        when(savedSubscription.getSubscriptionId())
                .thenReturn(subscriptionId);
        when(savedSubscription.getOfferingId()).thenReturn(offeringId);
        when(savedSubscription.getQuantity()).thenReturn(10L);
        when(savedSubscription.getPricePerUnit()).thenReturn(10_000L);
        when(savedSubscription.getAmount()).thenReturn(100_000L);
        when(savedSubscription.getSubscriptionStatus())
                .thenReturn(SubscriptionStatus.PROCESSING);

        when(subscriptionRepository.saveAndFlush(any()))
                .thenReturn(savedSubscription);

        when(idempotencyRequestRepository.complete(
                userId,
                IdempotencyOperation.CREATE_SUBSCRIPTION.name(),
                idempotencyKey,
                subscriptionId,
                HttpStatus.ACCEPTED.value()
        )).thenReturn(1);

        // when
        SubscriptionCreateResponse response =
                subscriptionTransactionService.createSubscription(
                        offeringId, userId, idempotencyKey, 10L, 10_000L,
                        correlationId
                );

        // then
        assertThat(response.subscriptionId()).isEqualTo(subscriptionId);
        verify(subscriptionEventPublisher)
                .publishReserved(savedSubscription, correlationId);
    }

    @Test
    @DisplayName("멱등 요청 완료 처리에 실패하면 예외가 발생한다")
    void throwsWhenIdempotencyCompletionFails() {
        // given
        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        String idempotencyKey = "fail-complete-key";

        when(subscriptionRepository
                .existsByOfferingIdAndUserIdAndIsDeletedFalse(
                        offeringId, userId
                ))
                .thenReturn(false);

        when(offeringRepository.reserveQuantity(
                offeringId, 10L, userId
        )).thenReturn(1);

        var savedSubscription =
                mock(com.moneykk.moneytown.offering.subscription.domain.entity.Subscription.class);
        when(savedSubscription.getSubscriptionId())
                .thenReturn(subscriptionId);

        when(subscriptionRepository.saveAndFlush(any()))
                .thenReturn(savedSubscription);

        when(idempotencyRequestRepository.complete(
                any(), any(), any(), any(), anyInt()
        )).thenReturn(0);

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subscriptionTransactionService.createSubscription(
                        offeringId, userId, idempotencyKey, 10L, 10_000L,
                        "correlation-id"
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(SubscriptionErrorCode.IDEMPOTENCY_COMPLETION_FAILED);
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