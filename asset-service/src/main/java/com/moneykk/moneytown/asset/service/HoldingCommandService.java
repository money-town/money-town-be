package com.moneykk.moneytown.asset.service;

import com.moneykk.moneytown.asset.dto.request.HoldingAllocationRequest;
import com.moneykk.moneytown.asset.dto.response.HoldingAllocationResponse;
import com.moneykk.moneytown.asset.dto.response.HoldingAllocationResult;
import com.moneykk.moneytown.asset.entity.Asset;
import com.moneykk.moneytown.asset.entity.Holding;
import com.moneykk.moneytown.asset.entity.HoldingHistory;
import com.moneykk.moneytown.asset.entity.HoldingHistoryType;
import com.moneykk.moneytown.asset.global.exception.AssetErrorCode;
import com.moneykk.moneytown.asset.repository.AssetRepository;
import com.moneykk.moneytown.asset.repository.HoldingHistoryRepository;
import com.moneykk.moneytown.asset.repository.HoldingRepository;
import com.moneykk.moneytown.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** 지분 배정·회수 서비스 */
@Service
@RequiredArgsConstructor
public class HoldingCommandService {

    private final AssetRepository assetRepository;
    private final HoldingRepository holdingRepository;
    private final HoldingHistoryRepository holdingHistoryRepository;

    @Transactional
    public HoldingAllocationResponse allocate(HoldingAllocationRequest request) {
        // 중복 청약 확인
        Optional<HoldingHistory> existingHistory =
                holdingHistoryRepository.findBySubscriptionIdAndHistoryType(
                        request.subscriptionId(),
                        HoldingHistoryType.ALLOCATE
                );

        if (existingHistory.isPresent()) {
            return alreadyProcessed(request, existingHistory.get());
        }

        // 자산 조회
        Asset asset = assetRepository.findByIdAndIsDeletedFalse(request.assetId())
                .orElseThrow(() -> new BusinessException(
                        AssetErrorCode.ASSET_NOT_FOUND
                ));

        // 자산 락을 기다리는 동안 같은 청약이 처리됐는지 다시 확인
        existingHistory = holdingHistoryRepository.findBySubscriptionIdAndHistoryType(
                request.subscriptionId(),
                HoldingHistoryType.ALLOCATE
        );
        if (existingHistory.isPresent()) {
            return alreadyProcessed(request, existingHistory.get());
        }

        // 자산 배정 수량 증가
        asset.allocateShares(request.quantity());

        // 기존 보유지분 조회 또는 생성
        Holding holding = holdingRepository
                .findByAssetIdAndUserId(request.assetId(), request.userId())
                .orElseGet(() -> new Holding(
                        request.assetId(),
                        request.userId(),
                        0
                ));

        long balanceBefore = holding.getQuantity();

        // 사용자 보유 수량 증가
        holding.allocate(request.quantity());
        holding = holdingRepository.save(holding);

        // 배정 이력 저장
        HoldingHistory history = new HoldingHistory(
                holding.getId(),
                request.subscriptionId(),
                HoldingHistoryType.ALLOCATE,
                request.quantity(),
                balanceBefore,
                holding.getQuantity(),
                "ALLOCATE:" + request.subscriptionId(),
                null
        );

        holdingHistoryRepository.save(history);

        return new HoldingAllocationResponse(
                request.subscriptionId(),
                holding.getId(),
                holding.getAssetId(),
                holding.getUserId(),
                request.quantity(),
                HoldingAllocationResult.ALLOCATED
        );
    }

    private HoldingAllocationResponse alreadyProcessed(
            HoldingAllocationRequest request,
            HoldingHistory history
    ) {
        Holding holding = holdingRepository.findById(history.getHoldingId())
                .orElseThrow(() -> new BusinessException(
                        AssetErrorCode.HOLDING_DATA_CONFLICT
                ));

        // 기존 처리 내용과 현재 요청이 같은지 확인
        if (!holding.getAssetId().equals(request.assetId())
                || !holding.getUserId().equals(request.userId())
                || history.getQuantity() != request.quantity()) {
            throw new BusinessException(
                    AssetErrorCode.HOLDING_DATA_CONFLICT
            );
        }

        return new HoldingAllocationResponse(
                request.subscriptionId(),
                holding.getId(),
                holding.getAssetId(),
                holding.getUserId(),
                history.getQuantity(),
                HoldingAllocationResult.ALREADY_PROCESSED
        );
    }
}
