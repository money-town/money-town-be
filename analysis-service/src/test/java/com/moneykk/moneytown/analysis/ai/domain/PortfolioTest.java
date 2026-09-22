package com.moneykk.moneytown.analysis.ai.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioTest {

    private Portfolio portfolio() {
        return Portfolio.builder()
                .idempotencyKey(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .investmentAmount(1_000_000L)
                .riskType(RiskType.MEDIUM)
                .assetType(null)
                .model("gpt-4o-mini")
                .promptVersion("v1")
                .build();
    }

    @Test
    @DisplayName("생성 직후에는 PENDING 상태다")
    void builder_startsAsPending() {
        assertThat(portfolio().getStatus()).isEqualTo(AiStatus.PENDING);
    }

    @Test
    @DisplayName("PENDING 상태에서 process하면 PROCESSING으로 전이된다")
    void process_fromPending_transitionsToProcessing() {
        Portfolio p = portfolio();

        p.process();

        assertThat(p.getStatus()).isEqualTo(AiStatus.PROCESSING);
    }

    @Test
    @DisplayName("PENDING이 아니면 process를 호출해도 아무 변화가 없다")
    void process_notPending_isNoOp() {
        Portfolio p = portfolio();
        p.process();
        p.complete("{}", 100L);

        p.process();

        assertThat(p.getStatus()).isEqualTo(AiStatus.COMPLETED);
    }

    @Test
    @DisplayName("PROCESSING 상태에서 complete하면 COMPLETED로 전이되고 응답이 기록된다")
    void complete_fromProcessing_transitionsToCompleted() {
        Portfolio p = portfolio();
        p.process();

        p.complete("{\"result\":\"ok\"}", 1500L);

        assertThat(p.getStatus()).isEqualTo(AiStatus.COMPLETED);
        assertThat(p.getResponse()).isEqualTo("{\"result\":\"ok\"}");
        assertThat(p.getProcessingTime()).isEqualTo(1500L);
        assertThat(p.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 종결된 상태에서 complete를 호출하면 아무 변화가 없다")
    void complete_alreadyFinished_isNoOp() {
        Portfolio p = portfolio();
        p.process();
        p.fail("먼저 실패", 100L);

        p.complete("{}", 200L);

        assertThat(p.getStatus()).isEqualTo(AiStatus.FAILED);
        assertThat(p.getResponse()).isNull();
    }

    @Test
    @DisplayName("PROCESSING 상태에서 fail하면 FAILED로 전이되고 에러 메시지가 기록된다")
    void fail_fromProcessing_transitionsToFailed() {
        Portfolio p = portfolio();
        p.process();

        p.fail("추천 가능한 공모가 없습니다.", 300L);

        assertThat(p.getStatus()).isEqualTo(AiStatus.FAILED);
        assertThat(p.getErrorMessage()).isEqualTo("추천 가능한 공모가 없습니다.");
        assertThat(p.getProcessingTime()).isEqualTo(300L);
        assertThat(p.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 종결된 상태에서 fail을 호출하면 아무 변화가 없다")
    void fail_alreadyFinished_isNoOp() {
        Portfolio p = portfolio();
        p.process();
        p.complete("{}", 100L);

        p.fail("나중 실패", 200L);

        assertThat(p.getStatus()).isEqualTo(AiStatus.COMPLETED);
        assertThat(p.getErrorMessage()).isNull();
    }
}
