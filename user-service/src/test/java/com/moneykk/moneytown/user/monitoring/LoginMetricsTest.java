package com.moneykk.moneytown.user.monitoring;

import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginMetricsTest {

    private final MockClock clock = new MockClock();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
    private final LoginMetrics metrics = new LoginMetrics(registry);

    @AfterEach
    void closeRegistry() {
        registry.close();
    }

    @Test
    void measuresElapsedTimeAndPreservesResult() {
        String result = metrics.record(LoginMetrics.Stage.USER_LOOKUP, () -> {
            clock.add(Duration.ofMillis(42));
            return "result";
        });

        assertThat(result).isEqualTo("result");
        Timer timer = timer("user_lookup", "success");
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(42);
        assertThat(timer("user_lookup", "failure").count()).isZero();
    }

    @Test
    void measuresFailedStageAndPreservesOriginalException() {
        IllegalStateException failure = new IllegalStateException("DB unavailable");

        assertThatThrownBy(() -> metrics.record(LoginMetrics.Stage.REFRESH_TOKEN_REPLACE, (Runnable) () -> {
            clock.add(Duration.ofMillis(17));
            throw failure;
        })).isSameAs(failure);

        assertThat(timer("refresh_token_replace", "failure").count()).isEqualTo(1);
        assertThat(timer("refresh_token_replace", "failure").totalTime(TimeUnit.MILLISECONDS)).isEqualTo(17);
        assertThat(timer("refresh_token_replace", "success").count()).isZero();
    }

    @Test
    void keepsFiniteTagsAndNeverTagsCredentials() {
        assertThat(registry.getMeters()).hasSize(8);
        registry.getMeters().forEach(meter -> assertThat(meter.getId().getTags())
                .extracting(tag -> tag.getKey())
                .containsExactlyInAnyOrder("stage", "outcome"));
    }

    private Timer timer(String stage, String outcome) {
        return registry.get(LoginMetrics.METRIC_NAME).tags("stage", stage, "outcome", outcome).timer();
    }
}
