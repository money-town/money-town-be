package com.moneykk.moneytown.offering.subscription.command.application;

import com.moneykk.moneytown.common.event.EventEnvelope;
import com.moneykk.moneytown.common.exception.BusinessException;
import com.moneykk.moneytown.offering.global.exception.OfferingErrorCode;
import com.moneykk.moneytown.offering.global.exception.SubscriptionErrorCode;
import com.moneykk.moneytown.offering.global.processed.ProcessedEventService;
import com.moneykk.moneytown.offering.offering.domain.entity.Offering;
import com.moneykk.moneytown.offering.offering.domain.repository.OfferingRepository;
import com.moneykk.moneytown.offering.subscription.domain.entity.Subscription;
import com.moneykk.moneytown.offering.subscription.domain.entity.SubscriptionStatus;
import com.moneykk.moneytown.offering.subscription.domain.repository.SubscriptionRepository;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.SubscriptionEventPublisher;
import com.moneykk.moneytown.offering.subscription.infrastructure.event.WalletHoldSucceededPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletHoldResultService {

    private static final String SUCCEEDED_EVENT_TYPE = "WalletHoldSucceeded";

    private final ProcessedEventService processedEventService;
    private final SubscriptionRepository subscriptionRepository;
    private final OfferingRepository offeringRepository;
    private final SubscriptionEventPublisher subscriptionEventPublisher;

    /**
     * 동결 성공 이벤트를 처리한다.
     *
     * ProcessedEventService가 트랜잭션을 시작하고,
     * 청약 변경과 후속 Outbox 저장까지 같은 트랜잭션에서 실행한다.
     *
     * @param consumerGroup 실제 Kafka Listener가 사용하는 Consumer Group
     * @return 새로운 수신 이벤트를 처리했으면 true,
     *         동일 eventId가 이미 처리됐으면 false
     */
    public boolean handleSucceeded(
            EventEnvelope<WalletHoldSucceededPayload> envelope,
            String consumerGroup
    ) {
        validateSucceededEvent(envelope);

        UUID subscriptionId = parseSubscriptionId(envelope.aggregateId());

        return processedEventService.processOnce(
                envelope,
                consumerGroup,
                () -> confirmSubscription(subscriptionId, envelope)
        );
    }

    /**
     * processOnce()가 연 트랜잭션 안에서 실행된다.
     */
    private void confirmSubscription(
            UUID subscriptionId,
            EventEnvelope<WalletHoldSucceededPayload> envelope
    ) {
        Subscription subscription = subscriptionRepository
                .findByIdForUpdate(subscriptionId)
                .orElseThrow(() -> new BusinessException(
                        SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND
                ));

        // 수신 이벤트가 실제 청약자의 지갑 처리 결과인지 확인한다.
        if (!subscription.getUserId().equals(envelope.userId())) {
            throw new IllegalArgumentException(
                    "동결 성공 이벤트의 userId가 청약자와 일치하지 않습니다."
            );
        }

        /*
         * 서로 다른 eventId로 같은 청약의 성공 결과가 도착하더라도
         * 이미 확정된 청약은 다시 확정하거나 이벤트를 다시 생성하지 않는다.
         */
        if (subscription.getSubscriptionStatus()
                == SubscriptionStatus.CONFIRMED) {
            log.info(
                    "이미 확정된 청약의 동결 성공 이벤트. "
                            + "subscriptionId={}, eventId={}",
                    subscriptionId,
                    envelope.eventId()
            );
            return;
        }

        /*
         * 보상 중이거나 종료된 청약을 다시 확정하지 않는다.
         * 현재는 예외를 전달하여 처리 완료 이력이 커밋되지 않도록 한다.
         */
        if (subscription.getSubscriptionStatus()
                != SubscriptionStatus.PROCESSING) {
            log.warn(
                    "자동 확정할 수 없는 청약에 동결 성공 이벤트 도착. "
                            + "subscriptionId={}, eventId={}, status={}",
                    subscriptionId,
                    envelope.eventId(),
                    subscription.getSubscriptionStatus()
            );

            throw new BusinessException(
                    SubscriptionErrorCode.SUBSCRIPTION_CONFIRMATION_NOT_ALLOWED
            );
        }

        /*
         * 청약이 참조하는 공모에서 assetId를 가져온다.
         * 수신 Payload나 클라이언트 입력을 assetId로 사용하지 않는다.
         */
        Offering offering = offeringRepository
                .findById(subscription.getOfferingId())
                .orElseThrow(() -> new BusinessException(
                        OfferingErrorCode.OFFERING_NOT_FOUND
                ));

        subscription.confirm(Instant.now());

        subscriptionEventPublisher.publishConfirmed(
                subscription,
                offering.getAssetId(),
                envelope.correlationId()
        );
    }

    private void validateSucceededEvent(
            EventEnvelope<WalletHoldSucceededPayload> envelope
    ) {
        Objects.requireNonNull(envelope, "envelope은 필수입니다.");
        Objects.requireNonNull(envelope.eventId(), "eventId는 필수입니다.");
        Objects.requireNonNull(envelope.userId(), "userId는 필수입니다.");
        Objects.requireNonNull(envelope.occurredAt(), "occurredAt은 필수입니다.");

        if (!SUCCEEDED_EVENT_TYPE.equals(envelope.eventType())) {
            throw new IllegalArgumentException(
                    "WalletHoldSucceeded 이벤트만 처리할 수 있습니다."
            );
        }

        if (envelope.correlationId() == null
                || envelope.correlationId().isBlank()) {
            throw new IllegalArgumentException(
                    "correlationId는 필수입니다."
            );
        }

        WalletHoldSucceededPayload payload = Objects.requireNonNull(
                envelope.payload(),
                "payload는 필수입니다."
        );

        if (payload.holdId() == null || payload.holdId() <= 0) {
            throw new IllegalArgumentException(
                    "holdId는 양수여야 합니다."
            );
        }

        if (payload.walletId() == null || payload.walletId() <= 0) {
            throw new IllegalArgumentException(
                    "walletId는 양수여야 합니다."
            );
        }

        if (!"HELD".equals(payload.status())) {
            throw new IllegalArgumentException(
                    "동결 성공 이벤트의 status는 HELD여야 합니다."
            );
        }
    }

    private UUID parseSubscriptionId(String aggregateId) {
        if (aggregateId == null || aggregateId.isBlank()) {
            throw new IllegalArgumentException(
                    "aggregateId는 필수입니다."
            );
        }

        try {
            return UUID.fromString(aggregateId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "aggregateId는 UUID 형식의 subscriptionId여야 합니다.",
                    e
            );
        }
    }
}