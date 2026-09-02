package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.request.RevenueTransferStatusRequest;
import com.moneykk.moneytown.asset.dto.response.RevenueTransferStatusResponse;
import com.moneykk.moneytown.asset.entity.Revenue;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.repository.RevenueRepository;
import com.moneykk.moneytown.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 수익 상태 변경 서비스 */
@Service
@RequiredArgsConstructor
public class RevenueCommandService {

    private final RevenueRepository revenueRepository;

    @Transactional
    public RevenueTransferStatusResponse updateTransferStatus(
            UUID revenueId,
            RevenueTransferStatusRequest request
    ) {
        // 변경할 수익 조회
        Revenue revenue = revenueRepository.findById(revenueId)
                .orElseThrow(() -> new BusinessException(
                        AssetErrorCode.REVENUE_NOT_FOUND
                ));

        // 요청 상태에 맞게 엔티티 상태 변경
        switch (request.transferStatus()) {
            case TRANSFERRED -> revenue.markTransferred();
            case FAILED -> revenue.markFailed(request.failureReason());
            case READY -> revenue.retry();
        }

        // JPA 변경 감지로 UPDATE 처리
        return RevenueTransferStatusResponse.from(revenue);
    }
}