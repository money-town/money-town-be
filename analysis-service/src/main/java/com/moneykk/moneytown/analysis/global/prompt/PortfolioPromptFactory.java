package com.moneykk.moneytown.analysis.global.prompt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.analysis.ai.command.dto.PortfolioCandidate;
import com.moneykk.moneytown.analysis.ai.domain.AssetType;
import com.moneykk.moneytown.analysis.ai.domain.RiskType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PortfolioPromptFactory {

    private final ObjectMapper objectMapper;

    public String system(){
        return """
            너는 RWA 조각투자 포트폴리오 어드바이저다.
            - 반드시 아래 '공모 후보 목록'에 있는 offeringId 로만 포트폴리오를 구성한다. 목록에 없는 상품 추천 절대 금지.
            - '공원 후보 목록' 전체를 적용할 필요는 없다 사용자의 입력과 너의 해석에 맞춰 공모 상품을 선택한다.
            - 모든 allocation 의 percentage(정수) 합계는 정확히 100 이어야 한다.
            - 사용자의 위험 성향(riskType)에 맞춘다: LOW=안정형, MEDIUM=중립형, HIGH=공격형.
            - assetType 이 여러 종류면 분산을 고려한다.
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
    }

    public String user(long investmentAmount, RiskType riskType, AssetType preferredAssetType,
                       List<PortfolioCandidate> candidates){
        try{
            String json = objectMapper.writeValueAsString(candidates);
            return """
                투자 금액: %d 원
                위험 성향: %s
                선호 자산 유형: %s

                공모 후보 목록(JSON):
                %s
                """.formatted(
                    investmentAmount,
                    riskType,
                    preferredAssetType != null ? preferredAssetType : "제한 없음",
                    json);
        }catch (JsonProcessingException e){
            throw new IllegalStateException("프롬프트 직렬화 실패", e);
        }
    }
}
