package com.moneykk.moneytown.offering.global.processed;

import com.moneykk.moneytown.common.event.EventEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProcessedEventService {

    private final ProcessedEventRepository processedEventRepository;

    /**
     * 처리 이력, 업무 변경, 후속 Outbox 저장을
     * 동일 트랜잭션으로 실행한다.
     *
     * @return 신규 이벤트를 처리했으면 true,
     *         이미 처리된 이벤트면 false
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean processOnce(
            EventEnvelope<?> envelope,
            String consumerGroup,
            Runnable businessAction
    ) {
        Objects.requireNonNull(envelope, "envelope은 필수입니다.");
        Objects.requireNonNull(envelope.eventId(), "eventId는 필수입니다.");
        Objects.requireNonNull(businessAction, "업무 처리는 필수입니다.");

        validateText(consumerGroup, "consumerGroup", 100);
        validateText(envelope.eventType(), "eventType", 100);

        UUID aggregateId = parseAggregateId(envelope.aggregateId());

        int inserted = processedEventRepository.insertIfAbsent(
                envelope.eventId(),
                consumerGroup,
                envelope.eventType(),
                aggregateId
        );

        if (inserted == 0) {
            return false;
        }

        // 청약 변경과 후속 Outbox 저장을 동기적으로 실행한다.
        // 예외를 삼키지 않아야 처리 이력까지 함께 롤백된다.
        businessAction.run();

        int completed = processedEventRepository.markCompleted(
                envelope.eventId(),
                consumerGroup
        );

        if (completed != 1) {
            throw new IllegalStateException(
                    "이벤트 처리 완료 기록에 실패했습니다. eventId="
                            + envelope.eventId()
            );
        }

        return true;
    }

    private UUID parseAggregateId(String aggregateId) {
        if (aggregateId == null || aggregateId.isBlank()) {
            throw new IllegalArgumentException("aggregateId는 필수입니다.");
        }

        try {
            return UUID.fromString(aggregateId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "처리 대상 aggregateId는 UUID여야 합니다.",
                    e
            );
        }
    }

    private void validateText(
            String value,
            String fieldName,
            int maxLength
    ) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + "은 비어 있을 수 없고 "
                            + maxLength + "자를 초과할 수 없습니다."
            );
        }
    }
}