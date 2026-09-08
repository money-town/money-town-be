# User Account Events v1

## 공통 규격

- Producer: User Service
- Consumer: Wallet Service
- Topic: `user.account-events.v1`
- Kafka message key: 대상 `userId` 문자열
- Kafka message value: `EventEnvelope<T>` JSON
- 전송 방식: at-least-once
- 순서 기준: 동일한 `userId`를 Kafka key로 사용하여 사용자 단위 순서 유지

`EventEnvelope<T>` 필드:

| 필드 | 형식 | 설명 |
| --- | --- | --- |
| eventId | UUID | 이벤트 중복 판별 식별자 |
| eventType | String | 이벤트 종류 |
| aggregateId | String | 대상 사용자 ID 문자열 |
| userId | UUID | 대상 사용자 ID |
| occurredAt | Instant | 이벤트 발생 시각 |
| correlationId | String | 요청 추적 ID |
| payload | Object | 이벤트별 추가 정보 |

HTTP 요청에서 `X-Correlation-Id`를 전달받은 경우 해당 값을 사용한다. HTTP 요청이 없는 내부 처리에서는 새로운 UUID 문자열을 생성한다.

## UserRegistered

- eventType: `UserRegistered`
- 발행 시점: 회원 정보와 Outbox 이벤트 저장 트랜잭션 커밋 이후 Publisher가 발행
- 처리 목적: Wallet Service 기본 지갑 생성
- payload: 빈 객체. 대상 사용자 식별은 Envelope의 `userId` 사용

```json
{
  "eventId": "8df239cf-2ed2-4b02-8a90-7e33d767cfc4",
  "eventType": "UserRegistered",
  "aggregateId": "4ef8763f-5aec-4860-99e2-b165ab854ded",
  "userId": "4ef8763f-5aec-4860-99e2-b165ab854ded",
  "occurredAt": "2026-09-09T03:00:00Z",
  "correlationId": "48ce37d3-e435-4109-bf52-d7e026cdb501",
  "payload": {}
}
```

Wallet Service는 해당 `userId`의 지갑이 이미 존재하면 생성을 생략하여 중복 소비를 허용한다.

## UserWithdrawn

- eventType: `UserWithdrawn`
- 발행 시점: 사용자 논리 삭제와 Outbox 이벤트 저장 트랜잭션 커밋 이후 Publisher가 발행
- 처리 목적: Wallet Service 지갑 논리 삭제
- payload: 탈퇴 처리자 `withdrawnBy`

```json
{
  "eventId": "d1668f06-d575-4ca9-a31f-546406d426f0",
  "eventType": "UserWithdrawn",
  "aggregateId": "4ef8763f-5aec-4860-99e2-b165ab854ded",
  "userId": "4ef8763f-5aec-4860-99e2-b165ab854ded",
  "occurredAt": "2026-09-09T03:10:00Z",
  "correlationId": "48ce37d3-e435-4109-bf52-d7e026cdb501",
  "payload": {
    "withdrawnBy": "cdd147ad-2710-4122-a4f0-52d00d800dfa"
  }
}
```

Wallet Service는 아직 삭제되지 않은 지갑만 한 번 삭제하고, 이미 삭제된 지갑은 처리를 생략한다.

## Outbox 상태

- `PENDING`: 발행 대기
- `PROCESSING`: Publisher가 발행 작업 선점
- `PUBLISHED`: Kafka 발행 성공
- `FAILED`: 재시도 한도 초과로 자동 발행 중단

Publisher는 재시도 시각이 지난 `PENDING` 이벤트를 선점하여 `PROCESSING`으로 변경한다. 발행 성공 시 `PUBLISHED`와 `publishedAt`을 기록한다. 발행 실패 시 재시도 한도 전까지 `PENDING`, `retryCount`, `nextRetryAt`, `lastError`를 기록하고 한도를 초과하면 `FAILED`로 변경한다.

## 연동 주의사항

현재 Wallet Service의 `UserRegisteredConsumer`는 3필드 flat 메시지를 사용한다. 실제 회원가입 이벤트를 연결할 때 `EventEnvelope<UserRegisteredPayload>` 형식으로 Consumer와 역직렬화 설정을 함께 변경해야 한다.
