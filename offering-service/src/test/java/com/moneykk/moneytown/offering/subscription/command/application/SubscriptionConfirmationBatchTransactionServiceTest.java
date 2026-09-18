package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionConfirmationBatchTransactionServiceTest {

    @Mock
    private OfferingRepository offeringRepository;

    @Mock
    private SubscriptionBatchConfirmationService
            subscriptionBatchConfirmationService;

    @InjectMocks
    private SubscriptionConfirmationBatchTransactionService service;

    @Test
    @DisplayName(
            "확정 대상 공모를 선점하면 "
                    + "청약 확정 배치 한 번을 실행한다"
    )
    void confirmsOneBatchForClaimedOffering() {
        // given
        Offering offering = mock(Offering.class);

        when(offeringRepository
                .findNextConfirmationTargetForUpdate())
                .thenReturn(Optional.of(offering));

        when(subscriptionBatchConfirmationService
                .confirmNextBatchIfReady(
                        same(offering),
                        anyString()
                ))
                .thenReturn(100);

        // when
        int confirmedCount =
                service.confirmNextBatch();

        // then
        assertThat(confirmedCount).isEqualTo(100);

        ArgumentCaptor<String> correlationIdCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(subscriptionBatchConfirmationService)
                .confirmNextBatchIfReady(
                        same(offering),
                        correlationIdCaptor.capture()
                );

        String correlationId =
                correlationIdCaptor.getValue();

        assertThat(correlationId)
                .isNotBlank();

        /*
         * 스케줄 재처리 추적 ID가 실제 UUID 형식인지 확인한다.
         */
        assertThat(UUID.fromString(correlationId))
                .isNotNull();
    }

    @Test
    @DisplayName(
            "확정 대상 공모가 없으면 "
                    + "청약 확정 서비스를 호출하지 않는다"
    )
    void returnsZeroWhenNoConfirmationTargetExists() {
        // given
        when(offeringRepository
                .findNextConfirmationTargetForUpdate())
                .thenReturn(Optional.empty());

        // when
        int confirmedCount =
                service.confirmNextBatch();

        // then
        assertThat(confirmedCount).isZero();

        verifyNoInteractions(
                subscriptionBatchConfirmationService
        );
    }
}