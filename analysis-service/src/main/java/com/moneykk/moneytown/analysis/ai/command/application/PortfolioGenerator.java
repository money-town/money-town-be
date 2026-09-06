package com.moneykk.moneytown.analysis.ai.command.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.analysis.ai.command.dto.PortfolioCandidate;
import com.moneykk.moneytown.analysis.ai.command.dto.PortfolioRecommendation;
import com.moneykk.moneytown.analysis.ai.domain.Portfolio;
import com.moneykk.moneytown.analysis.ai.domain.repository.PortfolioRepository;
import com.moneykk.moneytown.analysis.ai.infrastructure.client.AssetServiceClient;
import com.moneykk.moneytown.analysis.ai.infrastructure.client.OfferingServiceClient;
import com.moneykk.moneytown.analysis.ai.infrastructure.client.dto.AssetSummary;
import com.moneykk.moneytown.analysis.ai.infrastructure.client.dto.OfferingSummary;
import com.moneykk.moneytown.analysis.global.prompt.PortfolioPromptFactory;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.response.PageResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortfolioGenerator {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioStore portfolioStore;
    private final OfferingServiceClient offeringServiceClient;
    private final AssetServiceClient assetServiceClient;
    private final ChatClient portfolioChatClient;
    private final PortfolioPromptFactory portfolioPromptFactory;
    private final ObjectMapper objectMapper;

    private static final int MAX_ATTEMPTS = 2;

    @Async("aiTaskExecutor")
    public void generate(UUID portfolioId) {
        long t0 = System.currentTimeMillis();
        try{
            Portfolio p = portfolioRepository.findByIdAndIsDeletedIsFalse(portfolioId)
                    .orElseThrow(() -> new IllegalArgumentException("포트폴리오 없음: " + portfolioId));

            // 1. 공모 목록 (실패 = 추천 불가 -> 예외로 fail 유도)
            List<OfferingSummary> offerings = fetchOpenOfferings();
            if(offerings.isEmpty()){
                portfolioStore.fail(portfolioId, "추천 가능한 공모가 없습니다.", elapsed(t0));
                return;
            }

            // 2. 자산 enrich (실패해도 계속)
            Map<UUID, AssetSummary> assetById = fetchAssets(offerings);

            // 3. 병합
            List<PortfolioCandidate> candidates = merge(offerings, assetById);
            Set<UUID> validOfferingIds = candidates.stream()
                    .map(PortfolioCandidate::offeringId)
                    .collect(Collectors.toSet());

            // 4. 프롬프트
            String sys = portfolioPromptFactory.system();
            String user = portfolioPromptFactory.user(
                    p.getInvestmentAmount(), p.getRiskType(), p.getAssetType(), candidates
            );

            // 5. 호출 + 검즘 (최대 2회)
            PortfolioRecommendation rec = callWithValidation(
                    sys, user, validOfferingIds, p.getInvestmentAmount()
            );

            // 6. 저장
            portfolioStore.complete(portfolioId,
                    objectMapper.writeValueAsString(rec), elapsed(t0));
            log.info("AI 포트폴리오 생성 완료 portfolioId={} ({}ms)", portfolioId, elapsed(t0));
        }catch (Exception e){
            log.error("AI 포트폴리오 생성 실패 portfolioId={}", portfolioId, e);
            portfolioStore.fail(portfolioId, e.getMessage(), elapsed(t0));
        }
    }

    // LLM 호출 + 검즘 + 재시도
    private PortfolioRecommendation callWithValidation(String sys, String user, Set<UUID> validOfferingIds, Long investmentAmount) {

        RuntimeException last = null;
        for(int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++){
            try{
                PortfolioRecommendation rec = portfolioChatClient.prompt()
                        .system(sys)
                        .user(user)
                        .call()
                        .entity(PortfolioRecommendation.class);
                validate(rec, validOfferingIds);
                return normalizeAmounts(rec, investmentAmount);
            }catch (RuntimeException e){
                last = e;
                log.warn("추천 생성 시도 {}/{} 실패 : {}", attempt, MAX_ATTEMPTS, e.getMessage());
            }
        }
        throw new IllegalArgumentException("추천 생성/검증 " + MAX_ATTEMPTS + "회 실패", last);

    }

    private PortfolioRecommendation normalizeAmounts(PortfolioRecommendation rec, Long investmentAmount) {
        List<PortfolioRecommendation.Allocation> fixed = rec.allocations().stream()
                .map(a -> new PortfolioRecommendation.Allocation(
                        a.offeringId(), a.title(), a.percentage(),
                        Math.round(investmentAmount * (a.percentage() / 100.0)),
                        a.reason()
                ))
                .toList();
        return new PortfolioRecommendation(fixed, rec.summary(), rec.disclaimer());
    }

    private void validate(PortfolioRecommendation rec, Set<UUID> validOfferingIds) {
        if(rec == null || rec.allocations() == null || rec.allocations().isEmpty()){
            throw new IllegalArgumentException("allocations가 비어있음");
        }
        for(PortfolioRecommendation.Allocation a: rec.allocations()){
            if(a.offeringId() == null || !validOfferingIds.contains(a.offeringId())){
                throw new IllegalArgumentException("후보에 없는 offeringId 추천(환각): " + a.offeringId());
            }
            if(a.percentage() <= 0){
                throw new IllegalArgumentException("percentage가 0 이하: " + a.percentage());
            }
        }
        int sum = rec.allocations().stream()
                .mapToInt(PortfolioRecommendation.Allocation::percentage).sum();
        if(sum < 99 || sum > 101){
            throw new IllegalArgumentException("배분 비율 합이 100이 아님: " + sum);
        }
    }

    // Feign 헬퍼
    private List<OfferingSummary> fetchOpenOfferings(){
        try{
            ApiResponse<PageResponse<OfferingSummary>> resp =
                    offeringServiceClient.getOpenOfferings("OPEN", 10, "endAt,asc");
            if(resp == null || !resp.success() || resp.data() == null){
                return List.of();
            }
            return resp.data().content();
        }catch (FeignException e){
            throw new IllegalArgumentException("공모 목록 조회 실패" , e);
        }
    }

    private Map<UUID, AssetSummary> fetchAssets(List<OfferingSummary> offerings){
        List<UUID> ids = offerings.stream()
                .map(OfferingSummary::assetId).distinct().toList();
        try{
            ApiResponse<List<AssetSummary>> resp = assetServiceClient.getAssets(ids);
            if (resp == null || !resp.success() || resp.data() == null) {
                return Map.of();
            }
            return resp.data().stream()
                    .collect(Collectors.toMap(AssetSummary::assetId, Function.identity(), (a, b) -> a));
        }catch (FeignException e){
            log.warn("자산 상세 조회 실패 - enrich 없이 진행", e);
            return Map.of();
        }
    }

    private List<PortfolioCandidate> merge(List<OfferingSummary> offerings,
                                           Map<UUID, AssetSummary> assetById){
        return offerings.stream().map(o ->{
            AssetSummary a = assetById.get(o.assetId());
            return new PortfolioCandidate(
                    o.offeringId(), o.title(), o.pricePerUnit(), o.remainingQuantity(), o.endAt(),
                    a != null ? a.assetType() : null,
                    a != null ? a.expectedReturnRate() : null,
                    a != null ? a.valuationAmount() : null,
                    a != null ? a.description() : null
            );
        }).toList();
    }
    private long elapsed(long t0){
        return System.currentTimeMillis() - t0;
    }
}
