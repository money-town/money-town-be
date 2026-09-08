package com.moneykk.moneytown.offering.global.processed;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class ProcessedEventRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 동일 이벤트와 Consumer Group의 처리 권한을 확보한다.
     *
     * @return 신규 INSERT면 1, 이미 존재하면 0
     */
    public int insertIfAbsent(
            UUID eventId,
            String consumerGroup,
            String eventType,
            UUID aggregateId
    ) {
        return entityManager.createNativeQuery("""
                INSERT INTO p_processed_events (
                    processed_event_id,
                    event_id,
                    consumer_group,
                    event_type,
                    aggregate_id,
                    processed_at
                )
                VALUES (
                    :processedEventId,
                    :eventId,
                    :consumerGroup,
                    :eventType,
                    :aggregateId,
                    CURRENT_TIMESTAMP
                )
                ON CONFLICT (event_id, consumer_group)
                DO NOTHING
                """)
                .setParameter("processedEventId", UUID.randomUUID())
                .setParameter("eventId", eventId)
                .setParameter("consumerGroup", consumerGroup)
                .setParameter("eventType", eventType)
                .setParameter("aggregateId", aggregateId)
                .executeUpdate();
    }

    /**
     * 업무 처리와 후속 Outbox 저장을 마친 시각을 기록한다.
     * 실제 커밋 시각이 아니라 트랜잭션 내 처리 완료 시각이다.
     */
    public int markCompleted(
            UUID eventId,
            String consumerGroup
    ) {
        return entityManager.createNativeQuery("""
                UPDATE p_processed_events
                   SET processed_at = clock_timestamp()
                 WHERE event_id = :eventId
                   AND consumer_group = :consumerGroup
                """)
                .setParameter("eventId", eventId)
                .setParameter("consumerGroup", consumerGroup)
                .executeUpdate();
    }
}