package com.moneykk.moneytown.analysis.ai.infrastructure.client;

import com.moneykk.moneytown.analysis.ai.infrastructure.client.dto.OfferingSummary;
import com.moneykk.moneytown.common.response.ApiResponse;
import com.moneykk.moneytown.common.response.PageResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "offering-service")
public interface OfferingServiceClient {
    @GetMapping("/api/v1/offerings")
    ApiResponse<PageResponse<OfferingSummary>> getOpenOfferings(
            @RequestParam("offeringStatus") String offeringStatus,   // "OPEN"
            @RequestParam("size") int size,                          // 10
            @RequestParam("sort") String sort);
}
