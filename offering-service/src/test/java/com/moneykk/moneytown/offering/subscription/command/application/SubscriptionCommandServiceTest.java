package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.command.dto.request.SubscriptionCreateRequest;
import com.moneykk.moneytown.offering.subscription.domain.repository.IdempotencyRequestRepository;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.domain.service.SubscriptionRequestHasher;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.AnalysisServiceClient;
import com.moneykk.moneytown.offering.subscription.infrastructure.client.UserServiceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SubscriptionCommandServiceTest {

    @Mock
    private OfferingRepository offeringRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionIdempotencyService subscriptionIdempotencyService;

    @Mock
    private SubscriptionTransactionService subscriptionTransactionService;

    @Mock
    private IdempotencyRequestRepository idempotencyRequestRepository;

    @Mock
    private SubscriptionRequestHasher subscriptionRequestHasher;

    @Mock
    private AnalysisServiceClient analysisServiceClient;

    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private SubscriptionCommandService subscriptionCommandService;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("유효하지 않은 Correlation-ID는 멱등 요청을 선점하기 전에 거부한다")
    void rejectsInvalidCorrelationIdBeforeBeginningIdempotency(
            String correlationId
    ) {
        SubscriptionCreateRequest request =
                new SubscriptionCreateRequest(10L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> subscriptionCommandService.create(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "test-idempotency-key",
                        request,
                        correlationId
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(
                        SubscriptionErrorCode.INVALID_SUBSCRIPTION_INPUT
                );

        // 핵심 검증: 멱등 요청 PROCESSING 행을 생성하지 않아야 한다.
        verifyNoInteractions(subscriptionIdempotencyService);

        verifyNoInteractions(subscriptionRequestHasher);
        verifyNoInteractions(subscriptionTransactionService);
        verifyNoInteractions(offeringRepository);
        verifyNoInteractions(subscriptionRepository);
        verifyNoInteractions(idempotencyRequestRepository);
        verifyNoInteractions(analysisServiceClient);
        verifyNoInteractions(userServiceClient);
    }
}