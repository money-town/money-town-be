package com.moneykk.moneytown.analysis.ai.infrastructure.client;

import com.moneykk.moneytown.analysis.ai.infrastructure.client.dto.OfferingSummary;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.response.PageResponse;
import com.moneykk.moneytown.common.security.AuthHeaderConstants;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "offering-service")
public interface OfferingServiceClient {

    @Retry(name = "offeringService")
    @GetMapping("/api/v1/internal/offerings/ai-portfolio-candidates")
    ApiResponse<List<OfferingSummary>> getAiPortfolioCandidates(
            @RequestHeader(AuthHeaderConstants.USER_ROLE) String role,
            @RequestParam int limit
    );
}
