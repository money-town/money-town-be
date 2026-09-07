package com.moneykk.moneytown.analysis.fds.command.application.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.analysis.ai.command.dto.PortfolioCandidate;
import com.moneykk.moneytown.analysis.ai.command.dto.PortfolioRecommendation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class PortfolioLlmTest {

    private final ObjectMapper om = new ObjectMapper().findAndRegisterModules();

    private ChatClient chatClient(){
        OpenAiApi api = OpenAiApi.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("gpt-4o-mini")
                        .temperature(0.7)
                        .build())
                .build();
        return ChatClient.create(model);
    }
    private String userPrompt() throws Exception {
        Instant now = Instant.now();

        // c1: 소진율 높음 + 대형 + 중수익 → 안정형 근거
        var c1 = new PortfolioCandidate(
                UUID.randomUUID(), "강남 오피스텔 조각투자",
                10_000L,            // pricePerUnit
                100_000L,           // totalQuantity
                15_000L,            // remainingQuantity
                85,                 // subscriptionRatePercent  (100000-15000)/100000
                1_000_000_000L,     // totalRaiseAmount  10_000 * 100_000
                5,                  // daysToClose
                now.plus(Duration.ofDays(5)),
                "REAL_ESTATE", new java.math.BigDecimal("6.5"),
                5_000_000_000L, "서울 강남 소재 오피스텔, 임대수익 기반");

        // c2: 소진율 중간 + 소형 + 고수익 + 마감 임박 → 공격형 근거
        var c2 = new PortfolioCandidate(
                UUID.randomUUID(), "아이유 음원 저작권",
                5_000L,
                40_000L,
                22_000L,
                45,                 // (40000-22000)/40000
                200_000_000L,       // 5_000 * 40_000
                1,                  // daysToClose
                now.plus(Duration.ofDays(1)),
                "MUSIC_COPYRIGHT", new java.math.BigDecimal("9.2"),
                800_000_000L, "인기 음원 저작권 스트리밍 수익 분배");

        // c3: 대형이지만 소진율 낮고 저수익 → 상대적으로 매력도 낮음
        var c3 = new PortfolioCandidate(
                UUID.randomUUID(), "부산 물류창고",
                20_000L,
                600_000L,
                480_000L,
                20,                 // (600000-480000)/600000
                12_000_000_000L,    // 20_000 * 600_000
                2,                  // daysToClose
                now.plus(Duration.ofDays(2)),
                "REAL_ESTATE", new java.math.BigDecimal("5.1"),
                12_000_000_000L, "부산항 인근 물류창고, 장기 임대 계약");

        return """
        투자 금액: 1000000 원
        위험 성향: MEDIUM
        선호 자산 유형: 제한 없음

        공모 후보 목록(JSON):
        %s
        """.formatted(om.writeValueAsString(List.of(c1, c2, c3)));
    }

    private static final String SYSTEM = """
        너는 RWA 조각투자 포트폴리오 어드바이저다.
        - 반드시 아래 '공모 후보 목록'에 있는 offeringId 로만 포트폴리오를 구성한다. 목록에 없는 상품 추천 절대 금지.
        - '공원 후보 목록' 전체를 적용할 필요는 없다 사용자의 입력과 너의 해석에 맞춰 공모 상품을 선택한다.
        - 모든 allocation 의 percentage(정수) 합계는 정확히 100 이어야 한다.
        - 사용자의 위험 성향(riskType)에 맞춘다: LOW=안정형, MEDIUM=중립형, HIGH=공격형.
        - 각 항목 reason 은 한국어 2문장 근거.
        - 근거는 있되 너에게 주어진 데이터를 너의 투자 철할에 맞게 생각하고 트렌드를 분석해서 답을 내놔라.
        - summary 는 한국어 2~3문장. disclaimer 에는 '본 추천은 투자 자문이 아니며 참고용입니다' 취지의 면책 문구 포함.
        - amount 는 임의로 채워도 된다. 서버가 percentage 기준으로 다시 계산한다.
        - 공모 후보 데이터 해석:
          * subscriptionRatePercent: 청약 소진율(%). 높을수록 시장 수요가 이미 검증된 상품.
          * totalRaiseAmount: 총 모집 규모(원). 클수록 상대적으로 안정적인 대형 딜.
          * daysToClose: 마감까지 남은 일수. 짧으면 참여 기회가 임박.
          * expectedReturnRate / assetType / description: 있는 경우에만 활용. assetType 이 여러 종류면 섞어서 분산.
        - 위험 성향별 배분 기준:
          * LOW  = 소진율 높고 모집 규모 큰 안정형 위주
          * HIGH = 소진율 낮아도 성장 여지 있는 상품 비중 확대 허용
          * MEDIUM = 둘의 균형
        - reason 에는 근거가 된 구체 수치를 언급한다 (예: "소진율 78%, 마감 2일 전").
        """;



    @Test
    void 구조화_파싱_출력() throws Exception{
        PortfolioRecommendation rec = chatClient().prompt()
                .system(SYSTEM)
                .user(userPrompt())
                .call()
                .entity(PortfolioRecommendation.class);
        System.out.println("==== PARSED ====\n"
        + om.writerWithDefaultPrettyPrinter().writeValueAsString(rec));
    }


}
