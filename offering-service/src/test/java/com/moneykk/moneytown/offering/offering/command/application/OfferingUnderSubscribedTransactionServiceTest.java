package com.moneykk.moneytown.offering.offering.command.application;

import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.entity.OfferingStatus;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferingUnderSubscribedTransactionServiceTest {

    @Mock
    private OfferingRepository offeringRepository;

    @Mock
    private OfferingCompensationCompletionService
            offeringCompensationCompletionService;

    @InjectMocks
    private OfferingUnderSubscribedTransactionService service;

    @Test
    @DisplayName("모집 미달 공모는 취소 처리 상태만 시작하고 청약 보상은 배치에 위임한다")
    void startsCancellationWithoutLoadingSubscriptions() {
        // given
        UUID offeringId = UUID.randomUUID();
        Instant now = Instant.now();

        Offering offering = mock(Offering.class);

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));
        when(offering.getOfferingStatus())
                .thenReturn(OfferingStatus.OPEN);
        when(offering.getEndAt())
                .thenReturn(now.minusSeconds(1));
        when(offering.getRemainingQuantity())
                .thenReturn(100L);

        // when
        boolean result = service.startUnderSubscribedCancellation(
                offeringId,
                now
        );

        // then
        assertThat(result).isTrue();

        verify(offering).startUnderSubscribedCancellation();
        verify(offeringCompensationCompletionService)
                .completeIfReady(offeringId);
    }

    @Test
    @DisplayName("잠금 대기 중 취소가 시작된 공모는 다시 처리하지 않는다")
    void skipsOfferingChangedAfterCandidateSelection() {
        // given
        UUID offeringId = UUID.randomUUID();
        Instant now = Instant.now();

        Offering offering = mock(Offering.class);

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.of(offering));
        when(offering.getOfferingStatus())
                .thenReturn(OfferingStatus.CANCELLING);

        // when
        boolean result = service.startUnderSubscribedCancellation(
                offeringId,
                now
        );

        // then
        assertThat(result).isFalse();
        verify(offering, never()).startUnderSubscribedCancellation();
        verifyNoInteractions(offeringCompensationCompletionService);
    }

    @Test
    @DisplayName("후보 조회 후 공모가 삭제되면 처리하지 않는다")
    void skipsMissingOffering() {
        // given
        UUID offeringId = UUID.randomUUID();

        when(offeringRepository.findByIdForUpdate(offeringId))
                .thenReturn(Optional.empty());

        // when
        boolean result = service.startUnderSubscribedCancellation(
                offeringId,
                Instant.now()
        );

        // then
        assertThat(result).isFalse();
        verifyNoInteractions(offeringCompensationCompletionService);
    }
}
