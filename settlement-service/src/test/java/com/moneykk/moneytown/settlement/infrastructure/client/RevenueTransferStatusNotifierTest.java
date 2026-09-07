package com.moneykk.moneytown.settlement.infrastructure.client;

import com.moneykk.moneytown.settlement.infrastructure.client.dto.RevenueTransferStatus;
import com.moneykk.moneytown.settlement.infrastructure.client.dto.RevenueTransferStatusUpdateRequest;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevenueTransferStatusNotifierTest {

    private static final UUID REVENUE_ID = UUID.randomUUID();

    @Mock
    private AssetServiceClient assetServiceClient;

    @InjectMocks
    private RevenueTransferStatusNotifier revenueTransferStatusNotifier;

    @Test
    @DisplayName("SYSTEM 권한으로 자산 서비스에 전달 완료(TRANSFERRED) 상태를 통보한다")
    void notifiesAssetServiceWithTransferredStatus() {
        revenueTransferStatusNotifier.notifyTransferred(REVENUE_ID);

        ArgumentCaptor<RevenueTransferStatusUpdateRequest> requestCaptor =
                ArgumentCaptor.forClass(RevenueTransferStatusUpdateRequest.class);
        verify(assetServiceClient).updateRevenueTransferStatus(eq("SYSTEM"), eq(REVENUE_ID), requestCaptor.capture());
        assertThat(requestCaptor.getValue().transferStatus()).isEqualTo(RevenueTransferStatus.TRANSFERRED);
        assertThat(requestCaptor.getValue().failureReason()).isNull();
    }

    @Test
    @DisplayName("자산 서비스 호출이 실패해도 예외를 전파하지 않는다 (MVP 생략 가능한 부가 통보)")
    void swallowsFailureWithoutPropagating() {
        Request dummyRequest = Request.create(Request.HttpMethod.PATCH, "/api/v1/assets/revenues/{id}/transfer-status",
                java.util.Map.of(), null, StandardCharsets.UTF_8, new RequestTemplate());
        when(assetServiceClient.updateRevenueTransferStatus(any(), any(), any()))
                .thenThrow(new FeignException.InternalServerError("boom", dummyRequest, null, null));

        assertThatCode(() -> revenueTransferStatusNotifier.notifyTransferred(REVENUE_ID))
                .doesNotThrowAnyException();
    }
}