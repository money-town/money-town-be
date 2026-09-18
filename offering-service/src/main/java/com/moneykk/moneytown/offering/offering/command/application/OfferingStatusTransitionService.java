package com.moneykk.moneytown.offering.offering.command.application;

import com.moneykk.moneytown.common.config.JpaAuditingConfig;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.offering.command.dto.response.OfferingCancellationResponse;
import com.moneykk.moneytown.offering.offering.command.scheduler.OfferingSchedulerMetrics;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.offering.domain.repository.projection.UnderSubscribedOfferingTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OfferingStatusTransitionService {

    private static final int TRANSITION_BATCH_SIZE = 100;

    private final OfferingRepository offeringRepository;

    /*
     * 관리자 긴급 중단에서 사용하는 기존 의존성입니다.
     */
    private final OfferingCompensationCompletionService offeringCompensationCompletionService;

    /*
     * 모집 미달 공모 한 건을 독립 트랜잭션으로 처리한다.
     */
    private final OfferingUnderSubscribedTransactionService offeringUnderSubscribedTransactionService;

    private final OfferingSchedulerMetrics offeringSchedulerMetrics;

    /**
     * 시작 시간이 도래한 SCHEDULED 공모를 OPEN으로 일괄 전환한다.
     */
    @Transactional
    public int openScheduledOfferings() {
        return offeringRepository.openScheduledOfferings(
                JpaAuditingConfig.SYSTEM_USER_ID
        );
    }

    /**
     * 모집 종료 시간이 도래한 SOLD_OUT 공모를 CLOSED로 일괄 전환한다.
     */
    @Transactional
    public int closeSoldOutOfferings() {
        return offeringRepository.closeSoldOutOfferings(
                JpaAuditingConfig.SYSTEM_USER_ID
        );
    }

    /**
     * 관리자 요청으로 공모 중단 절차를 시작한다.
     *
     * HTTP 요청 트랜잭션에서는 공모를 CANCELLING으로 전환하고
     * 빠르게 커밋한다.
     *
     * 보상 대상 청약의 COMPENSATING 전환, 보상 엔티티 및
     * Outbox 생성은 OfferingCancellationBatchScheduler가
     * 제한된 크기의 독립 트랜잭션으로 처리한다.
     *
     * 보상 대상 청약이 없는 경우에는 같은 트랜잭션에서
     * 공모를 즉시 CANCELLED로 완료한다.
     */
    @Transactional
    public OfferingCancellationResponse cancelByAdmin(
            UUID offeringId,
            String correlationId
    ) {
        validateAdminCancellationInput(
                offeringId,
                correlationId
        );

        Offering offering = offeringRepository
                .findByIdForUpdate(offeringId)
                .orElseThrow(
                        () -> new BusinessException(
                                OfferingErrorCode.OFFERING_NOT_FOUND
                        )
                );

        offering.startAdminCancellation();

        /*
         * HTTP 요청 correlationId는 관리자 요청 자체를 추적하는 데
         * 사용한다.
         *
         * 이후 비동기 보상 배치는 offeringId를 안정적인
         * workflow correlationId로 사용한다.
         */
        log.info(
                "관리자 공모 중단 요청 접수. "
                        + "offeringId={}, requestCorrelationId={}, "
                        + "workflowCorrelationId={}",
                offeringId,
                correlationId,
                offeringId
        );

        /*
         * 청약이 하나도 없거나 모든 청약이 이미 최종 해결된 경우에는
         * 별도 배치를 기다리지 않고 즉시 CANCELLED로 완료한다.
         *
         * 미해결 청약이 있으면 CANCELLING 상태를 유지하고
         * 스케줄러가 보상 배치를 시작한다.
         */
        offeringCompensationCompletionService.completeIfReady(
                offeringId
        );

        return OfferingCancellationResponse.from(offering);
    }

    /**
     * 모집 종료 시간이 도래했지만 잔여 수량이 남은 공모를
     * 키셋 방식으로 100건씩 조회하여 모집 미달 취소를 시작한다.
     *
     * 공모별 처리는 독립 트랜잭션에서 수행한다. 특정 공모가 실패해
     * 조회 조건에 남더라도 같은 실행에서 후속 공모 처리를 계속한다.
     *
     * @return 실제로 모집 미달 취소 처리를 시작한 공모 수
     */
    public int startUnderSubscribedCancellations() {

        Instant now = Instant.now();

        Instant lastEndAt = null;
        UUID lastOfferingId = null;

        int processedCount = 0;

        while (true) {
            List<UnderSubscribedOfferingTarget> targets;

            if (lastEndAt == null) {
                targets =
                        offeringRepository
                                .findUnderSubscribedOfferingTargets(
                                        now,
                                        PageRequest.of(
                                                0,
                                                TRANSITION_BATCH_SIZE
                                        )
                                );
            } else {
                targets =
                        offeringRepository
                                .findUnderSubscribedOfferingTargetsAfter(
                                        now,
                                        lastEndAt,
                                        lastOfferingId,
                                        PageRequest.of(
                                                0,
                                                TRANSITION_BATCH_SIZE
                                        )
                                );
            }

            if (targets.isEmpty()) {
                break;
            }

            for (UnderSubscribedOfferingTarget target : targets) {
                try {
                    boolean processed =
                            offeringUnderSubscribedTransactionService
                                    .startUnderSubscribedCancellation(
                                            target.offeringId(),
                                            now
                                    );

                    if (processed) {
                        processedCount++;
                    }
                } catch (Exception e) {
                    offeringSchedulerMetrics
                            .recordUnderSubscribedItemFailure();

                    /*
                     * 현재 대상이 실패해도 키셋 커서를 전진시켜
                     * 같은 실행에서 후속 공모 처리를 계속한다.
                     */
                    log.error(
                            "모집 미달 공모 취소 처리 실패. offeringId={}",
                            target.offeringId(),
                            e
                    );
                }
            }

            UnderSubscribedOfferingTarget lastTarget =
                    targets.get(targets.size() - 1);

            lastEndAt = lastTarget.endAt();
            lastOfferingId = lastTarget.offeringId();

            if (targets.size() < TRANSITION_BATCH_SIZE) {
                break;
            }
        }

        return processedCount;
    }

    private void validateAdminCancellationInput(
            UUID offeringId,
            String correlationId
    ) {
        if (offeringId == null
                || correlationId == null
                || correlationId.isBlank()) {
            throw new BusinessException(
                    OfferingErrorCode.INVALID_OFFERING_INPUT
            );
        }
    }
}