package com.moneykk.moneytown.wallet.scheduler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.moneykk.moneytown.wallet.service.WalletBalanceMismatch;
import com.moneykk.moneytown.wallet.service.WalletLedgerReconciliationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletLedgerReconciliationSchedulerTest {

    @Mock
    private WalletLedgerReconciliationService walletLedgerReconciliationService;

    @InjectMocks
    private WalletLedgerReconciliationScheduler scheduler;

    private Logger schedulerLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUpLogCapture() {
        schedulerLogger = (Logger) LoggerFactory.getLogger(WalletLedgerReconciliationScheduler.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        schedulerLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDownLogCapture() {
        schedulerLogger.detachAppender(logAppender);
    }

    @Test
    @DisplayName("불일치가 없으면 INFO 로그만 남긴다")
    void reconcile_noMismatches_logsInfo() {
        when(walletLedgerReconciliationService.findMismatches()).thenReturn(List.of());

        scheduler.reconcile();

        assertThat(logAppender.list).hasSize(1);
        assertThat(logAppender.list.get(0).getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    @DisplayName("불일치가 있으면 ERROR 로그로 건수와 내용을 남긴다")
    void reconcile_hasMismatches_logsError() {
        WalletBalanceMismatch mismatch = new WalletBalanceMismatch(1L, 10_000L, 9_000L);
        when(walletLedgerReconciliationService.findMismatches()).thenReturn(List.of(mismatch));

        scheduler.reconcile();

        verify(walletLedgerReconciliationService).findMismatches();
        assertThat(logAppender.list).hasSize(1);
        ILoggingEvent logEvent = logAppender.list.get(0);
        assertThat(logEvent.getLevel()).isEqualTo(Level.ERROR);
        assertThat(logEvent.getFormattedMessage()).contains("1건").contains(mismatch.toString());
    }
}
