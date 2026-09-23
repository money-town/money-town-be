package com.moneykk.moneytown.analysis.ai.command.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioQueueDispatcherTest {

    @Mock
    private PortfolioStore portfolioStore;
    @Mock
    private PortfolioGenerator portfolioGenerator;
    @Mock
    private PortfolioNotificationDispatcher portfolioNotificationDispatcher;
    @Mock
    private ThreadPoolTaskExecutor aiTaskExecutor;

    private PortfolioQueueDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new PortfolioQueueDispatcher(
                portfolioStore, portfolioGenerator, portfolioNotificationDispatcher, aiTaskExecutor
        );
    }

    @Test
    @DisplayName("가용 워커가 없으면 클레임을 시도하지 않는다")
    void dispatch_noAvailableSlots_doesNotClaim() {
        when(aiTaskExecutor.getMaxPoolSize()).thenReturn(5);
        when(aiTaskExecutor.getActiveCount()).thenReturn(5);

        dispatcher.dispatch();

        verify(portfolioStore, never()).claimBatch(anyInt());
        verify(aiTaskExecutor, never()).execute(any());
    }

    @Test
    @DisplayName("클레임된 PENDING이 없으면 아무것도 제출하지 않는다")
    void dispatch_noPendingClaimed_submitsNothing() {
        when(aiTaskExecutor.getMaxPoolSize()).thenReturn(5);
        when(aiTaskExecutor.getActiveCount()).thenReturn(2);
        when(portfolioStore.claimBatch(3)).thenReturn(List.of());

        dispatcher.dispatch();

        verify(aiTaskExecutor, never()).execute(any());
    }

    @Test
    @DisplayName("가용 워커 수만큼 클레임해서 각각 generate 후 notify를 실행한다")
    void dispatch_claimedIds_executesGenerateThenNotifyForEach() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(aiTaskExecutor.getMaxPoolSize()).thenReturn(5);
        when(aiTaskExecutor.getActiveCount()).thenReturn(3);
        when(portfolioStore.claimBatch(2)).thenReturn(List.of(id1, id2));

        dispatcher.dispatch();

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(aiTaskExecutor, times(2)).execute(captor.capture());
        captor.getAllValues().forEach(Runnable::run);

        InOrder order1 = inOrder(portfolioGenerator, portfolioNotificationDispatcher);
        order1.verify(portfolioGenerator).generate(id1);
        order1.verify(portfolioNotificationDispatcher).notify(id1);

        InOrder order2 = inOrder(portfolioGenerator, portfolioNotificationDispatcher);
        order2.verify(portfolioGenerator).generate(id2);
        order2.verify(portfolioNotificationDispatcher).notify(id2);
    }

    @Test
    @DisplayName("워커풀 제출이 거절되면 해당 건을 즉시 실패 처리하고 예외를 전파하지 않는다")
    void dispatch_submissionRejected_failsPortfolioWithoutPropagating() {
        UUID id = UUID.randomUUID();
        when(aiTaskExecutor.getMaxPoolSize()).thenReturn(5);
        when(aiTaskExecutor.getActiveCount()).thenReturn(4);
        when(portfolioStore.claimBatch(1)).thenReturn(List.of(id));
        doThrow(new RejectedExecutionException("queue full")).when(aiTaskExecutor).execute(any());

        assertThatCode(() -> dispatcher.dispatch()).doesNotThrowAnyException();

        verify(portfolioStore).fail(eq(id), any(), eq(0L));
    }
}
