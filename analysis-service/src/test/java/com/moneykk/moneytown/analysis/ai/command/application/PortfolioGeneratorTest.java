package com.moneykk.moneytown.analysis.ai.command.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneykk.moneytown.analysis.ai.command.application.PortfolioGenerator;
import com.moneykk.moneytown.analysis.ai.command.application.PortfolioStore;
import com.moneykk.moneytown.analysis.ai.command.dto.PortfolioRecommendation;
import com.moneykk.moneytown.analysis.ai.domain.Portfolio;
import com.moneykk.moneytown.analysis.ai.domain.RiskType;
import com.moneykk.moneytown.analysis.ai.domain.repository.PortfolioRepository;
import com.moneykk.moneytown.analysis.ai.infrastructure.client.AssetServiceClient;
import com.moneykk.moneytown.analysis.ai.infrastructure.client.OfferingServiceClient;
import com.moneykk.moneytown.analysis.ai.infrastructure.client.dto.OfferingSummary;
import com.moneykk.moneytown.analysis.global.prompt.PortfolioPromptFactory;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.response.PageResponse;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;

@ExtendWith(MockitoExtension.class)
public class PortfolioGeneratorTest {

    @Mock private PortfolioRepository portfolioRepository;
    @Mock private PortfolioStore portfolioStore;
    @Mock private OfferingServiceClient offeringServiceClient;
    @Mock private AssetServiceClient assetServiceClient;
    @Mock private ChatClient portfolioChatClient;
    @Mock private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock private ChatClient.CallResponseSpec callResponseSpec;
    @Mock private PortfolioPromptFactory portfolioPromptFactory;

    private PortfolioGenerator portfolioGenerator;

    private final UUID portfolioId = UUID.randomUUID();
    private final UUID offeringId = UUID.randomUUID();

    @BeforeEach
    void setUp(){
        portfolioGenerator = new PortfolioGenerator(
                portfolioRepository, portfolioStore,
                offeringServiceClient, assetServiceClient,
                portfolioChatClient, portfolioPromptFactory,
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(portfolioGenerator, "assetEnrichEnabled", false);

        when(portfolioRepository.findByIdAndIsDeletedIsFalse(portfolioId))
                .thenReturn(Optional.of(portfolio()));
        lenient().when(portfolioPromptFactory.system()).thenReturn("SYSTEM");
        lenient().when(portfolioPromptFactory.user(anyLong(), any(), any(), any())).thenReturn("USER");
    }

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

    private ApiResponse<PageResponse<OfferingSummary>> offeringsResponse(List<OfferingSummary> list) {
        return ApiResponse.success(
                new PageResponse<>(list, 0, 10, list.size(), 1, true, true, false),
                null
        );
    }

    private OfferingSummary offering() {
        return new OfferingSummary(offeringId, UUID.randomUUID(), "테스트 공모",
                10_000L, 100L, 50L, Instant.now().plusSeconds(86_400));
    }

    private void stubChatClient(PortfolioRecommendation first, PortfolioRecommendation... rest) {
        when(portfolioChatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.entity(PortfolioRecommendation.class)).thenReturn(first, rest);
    }

    private PortfolioRecommendation validRecommendation() {
        return new PortfolioRecommendation(
                List.of(new PortfolioRecommendation.Allocation(offeringId, "테스트 공모", 100, 0, "이유")),
                "요약", "면책 문구"
        );
    }

    private PortfolioRecommendation hallucinatedRecommendation() {
        return new PortfolioRecommendation(
                List.of(new PortfolioRecommendation.Allocation(UUID.randomUUID(), "없는 상품", 100, 0, "이유")),
                "요약", "면책 문구"
        );
    }


    private FeignException feignServiceUnavailable() {
        Request request = Request.create(Request.HttpMethod.GET, "/", Map.of(), null, StandardCharsets.UTF_8);
        Response response = Response.builder()
                .status(503).reason("Service Unavailable")
                .request(request).headers(Map.of())
                .build();
        return FeignException.errorStatus("AssetServiceClient#getAssets", response);
    }

    @Test
    @DisplayName("정상 흐름: LLM 1차 성공 시 complete() 호출, 금액이 investmentAmount와 정확히 일치한다")
    void generate_success_completesWithNormalizedAmount() throws Exception {
        when(offeringServiceClient.getOpenOfferings(any(), anyInt(), any()))
                .thenReturn(offeringsResponse(List.of(offering())));
        stubChatClient(validRecommendation());
        when(portfolioStore.complete(eq(portfolioId), anyString(), anyLong())).thenReturn(true);

        portfolioGenerator.generate(portfolioId);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(portfolioStore).complete(eq(portfolioId), captor.capture(), anyLong());
        PortfolioRecommendation saved = new ObjectMapper().readValue(captor.getValue(), PortfolioRecommendation.class);
        assertThat(saved.allocations().get(0).amount()).isEqualTo(1_000_000L);
        verify(portfolioStore, never()).fail(any(), any(), anyLong());
    }


    @Test
    @DisplayName("공모 목록이 비어있으면 LLM 호출 없이 fail() 처리한다")
    void generate_noOfferings_fails() {
        when(offeringServiceClient.getOpenOfferings(any(), anyInt(), any()))
                .thenReturn(offeringsResponse(List.of()));

        portfolioGenerator.generate(portfolioId);

        verify(portfolioStore).fail(eq(portfolioId), contains("추천 가능한 공모가 없습니다"), anyLong());
        verifyNoInteractions(portfolioChatClient);
    }

    @Test
    @DisplayName("공모 조회 자체가 실패하면 fail() 처리한다")
    void generate_offeringFetchThrows_fails() {
        when(offeringServiceClient.getOpenOfferings(any(), anyInt(), any()))
                .thenThrow(new RuntimeException("offering-service down"));

        portfolioGenerator.generate(portfolioId);

        verify(portfolioStore).fail(eq(portfolioId), any(), anyLong());
        verifyNoInteractions(portfolioChatClient);
    }

    @Test
    @DisplayName("asset enrich가 켜져 있어도 자산 조회(FeignException)가 실패하면 enrich 없이 계속 진행해 완료된다")
    void generate_assetFetchFails_degradesAndCompletes() {
        ReflectionTestUtils.setField(portfolioGenerator, "assetEnrichEnabled", true);
        when(offeringServiceClient.getOpenOfferings(any(), anyInt(), any()))
                .thenReturn(offeringsResponse(List.of(offering())));
        when(assetServiceClient.getAssets(any(), any())).thenThrow(feignServiceUnavailable());
        stubChatClient(validRecommendation());
        when(portfolioStore.complete(eq(portfolioId), anyString(), anyLong())).thenReturn(true);

        portfolioGenerator.generate(portfolioId);

        verify(portfolioStore).complete(eq(portfolioId), anyString(), anyLong());
        verify(portfolioStore, never()).fail(any(), any(), anyLong());
    }

    @Test
    @DisplayName("LLM 1차 응답이 검증 실패해도(환각 offeringId) 2차 시도가 성공하면 complete() 된다")
    void generate_firstAttemptInvalid_secondSucceeds() {
        when(offeringServiceClient.getOpenOfferings(any(), anyInt(), any()))
                .thenReturn(offeringsResponse(List.of(offering())));
        stubChatClient(hallucinatedRecommendation(), validRecommendation());
        when(portfolioStore.complete(eq(portfolioId), anyString(), anyLong())).thenReturn(true);

        portfolioGenerator.generate(portfolioId);

        verify(callResponseSpec, times(2)).entity(PortfolioRecommendation.class);
        verify(portfolioStore).complete(eq(portfolioId), anyString(), anyLong());
    }

    @Test
    @DisplayName("LLM이 2회 모두 검증 실패하면 fail() 처리한다")
    void generate_bothAttemptsInvalid_fails() {
        when(offeringServiceClient.getOpenOfferings(any(), anyInt(), any()))
                .thenReturn(offeringsResponse(List.of(offering())));
        stubChatClient(hallucinatedRecommendation(), hallucinatedRecommendation());

        portfolioGenerator.generate(portfolioId);

        verify(callResponseSpec, times(2)).entity(PortfolioRecommendation.class);
        verify(portfolioStore).fail(eq(portfolioId), any(), anyLong());
    }
}
