package com.moneykk.moneytown.user.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class LoginMetrics {

    public static final String METRIC_NAME = "user.login.stage.duration";

    private final MeterRegistry registry;
    private final Map<Stage, Timer> successTimers = new EnumMap<>(Stage.class);
    private final Map<Stage, Timer> failureTimers = new EnumMap<>(Stage.class);

    public LoginMetrics(MeterRegistry registry) {
        this.registry = registry;
        for (Stage stage : Stage.values()) {
            successTimers.put(stage, timer(stage, "success"));
            failureTimers.put(stage, timer(stage, "failure"));
        }
    }

    // CPU 실행 시간뿐 아니라 스케줄링/DB 대기를 포함하는 경과 시간이다.
    public <T> T record(Stage stage, Supplier<T> action) {
        Timer.Sample sample = Timer.start(registry);
        boolean success = false;
        try {
            T result = action.get();
            success = true;
            return result;
        } finally {
            sample.stop((success ? successTimers : failureTimers).get(stage));
        }
    }

    public void record(Stage stage, Runnable action) {
        record(stage, () -> {
            action.run();
            return null;
        });
    }

    private Timer timer(Stage stage, String outcome) {
        return Timer.builder(METRIC_NAME)
                .description("Login stage elapsed time, including scheduling and I/O waits")
                // 이메일, userId, 토큰, correlationId는 고유값이 많으므로 태그로 사용하지 않는다.
                .tag("stage", stage.tag)
                .tag("outcome", outcome)
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(30))
                .register(registry);
    }

    public enum Stage {
        USER_LOOKUP("user_lookup"),
        PASSWORD_VERIFY("password_verify"),
        TOKEN_ISSUE("token_issue"),
        REFRESH_TOKEN_REPLACE("refresh_token_replace");

        private final String tag;

        Stage(String tag) {
            this.tag = tag;
        }
    }
}
