package com.moneykk.moneytown.analysis.notification.command.application;

import com.moneykk.moneytown.analysis.notification.command.application.NotificationStaleReaper;
import com.moneykk.moneytown.analysis.notification.domain.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationStaleReaperTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationStaleReaper reaper;

    @BeforeEach
    void setUp() {
        reaper = new NotificationStaleReaper(notificationRepository);
        ReflectionTestUtils.setField(reaper, "staleTimeout", Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("staleTimeout만큼 이전 시각을 기준으로 정체된 PENDING 알림을 조회한다")
    void failStaleProcessing_usesStaleTimeoutAsThreshold() {
        when(notificationRepository.failStaleProcessing(anyString(), any(), any())).thenReturn(0);

        Instant before = Instant.now();
        reaper.failStaleProcessing();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> nowCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> thresholdCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(notificationRepository).failStaleProcessing(anyString(), nowCaptor.capture(), thresholdCaptor.capture());

        assertThat(nowCaptor.getValue()).isBetween(before, after);
        assertThat(thresholdCaptor.getValue()).isEqualTo(nowCaptor.getValue().minus(Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("정체된 건이 있으면 정상적으로 처리되고 예외가 나지 않는다")
    void failStaleProcessing_someFailed_completesWithoutError() {
        when(notificationRepository.failStaleProcessing(anyString(), any(), any())).thenReturn(2);

        reaper.failStaleProcessing();

        verify(notificationRepository).failStaleProcessing(anyString(), any(), any());
    }
}
