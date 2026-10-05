# 포트폴리오 — STAR 정리

## 지갑 응답 불일치를 무분별한 재시도로부터 격리하다: DEAD_LETTER 사유 세분화

**Situation**

최종 정산(원금 반환) 지급은 `FinalSettlementDisbursementService`가 지갑 서비스에 Feign으로 동기 호출(`depositSettlement`)한 뒤, 응답에 담긴 `finalSettlementBatchId`가 요청과 일치하는지 확인한다. 지갑 호출이 3회 재시도(`MAX_RETRY_COUNT`)를 초과하면 `DEAD_LETTER`로 전환되고, 관리자는 재처리 API(`retryFinalSettlement`)로 회차의 `DEAD_LETTER` 건을 일괄 재시도할 수 있었다. 그런데 이 `DEAD_LETTER` 상태 하나에 성격이 전혀 다른 두 실패가 섞여 있었다 — "지갑 호출이 계속 실패한 경우(재시도하면 해결될 수 있음)"와 "지갑이 `success=true`를 반환했지만 응답의 `finalSettlementBatchId`가 요청과 다른 경우"다.

**Task**

후자(응답 불일치)는 지갑 쪽에서 실제로 무엇이 처리됐는지 알 수 없는 상태라, 그대로 재시도하면 이미 처리된 지급을 중복으로 다시 요청할 위험이 있다. 재시도로 해결 가능한 실패와, 사람이 지갑 트랜잭션을 대조한 뒤에만 처리해야 하는 실패를 DB 레벨에서 구분하고, 재처리 API가 후자를 자동으로 걸러내도록 만든다.

**Action**

1. **원인 분리** — `FinalSettlementDisbursementService.attempt()`에서 지갑 응답이 `null`이거나 응답의 `finalSettlementBatchId`가 요청과 다르면 일반 실패(`markFailedAttempt`)가 아니라 별도 경로(`markResponseMismatch`)로 분기했다.
2. **DB 스키마 변경** — `V20__add_final_settlement_payout_dead_letter_reason.sql`로 `p_final_settlement_payouts`에 `dead_letter_reason` 컬럼(`RETRY_EXCEEDED` / `RESPONSE_MISMATCH`)을 추가했다. 마이그레이션 시점에 이미 `DEAD_LETTER`였던 기존 행은 사유를 알 수 없으므로 `NULL`로 남기고, `NULL`은 하위 호환을 위해 기존처럼 재시도 가능한 것으로 취급했다.
3. **재처리 API 방어** — `FinalSettlementCommandService.findRetryablePayouts()`가 지급 건 ID를 생략한 요청에는 회차의 `DEAD_LETTER` 중 `RESPONSE_MISMATCH`를 자동으로 제외하고 나머지만 재시도 대상으로 삼는다. 반대로 관리자가 `RESPONSE_MISMATCH` 건의 ID를 직접 지정해 재시도를 요청하면 `SETTLEMENT_409_15`(`FINAL_SETTLEMENT_PAYOUT_MANUAL_RESOLUTION_REQUIRED`)로 명시적으로 거절해, "자동 제외"와 "명시적 차단" 두 경로 모두를 막았다.
4. **가시성 확보** — 조회 응답(`FinalSettlementPayoutListItemResponse`)에 `deadLetterReason`, `manualResolutionRequired` 필드를 노출해, 관리자가 회차 상세 화면에서 바로 "재시도 가능 건"과 "수동 대조가 필요한 건"을 구분할 수 있게 했다.

**Result**

- 응답 불일치 건이 일반 재시도 경로에서 API 레벨까지 이중으로 차단되어, 중복 지급으로 이어질 수 있는 경로가 원천적으로 막혔다.
- 처리 절차가 "지갑 트랜잭션 대조 → 수동 지급 → abandon 처리"로 명확히 분리되어, 관리자가 실수로 위험한 재시도를 누를 수 있는 여지 자체를 없앴다.
- 이 구분은 **최종 정산(원금 반환)에 한정**된다. 배당 지급은 Kafka 이벤트 왕복(`dividend-payout-dispatch-requested` → `wallet-dividend-result`) 구조라, 동기 Feign 응답을 즉시 대조하는 지금과 같은 "응답 불일치" 상황 자체가 발생하지 않기 때문이다.

**Learn**

실패를 하나의 상태(`DEAD_LETTER`)로 뭉쳐두면 "재시도해도 되는 실패"와 "재시도하면 위험한 실패"가 똑같이 취급된다. 사유별로 분리하는 것만으로는 부족하고, 위험한 사유는 재처리 API 레벨에서부터 막아야 관리자의 주의력에 기대지 않는 안전장치가 된다는 것을 확인했다.

---

## 실패한 배당 회차가 자산을 영구 차단하지 않도록: 재통보·에스컬레이션 도입

**Situation**

배당 정산은 자산별로 진행 중인 배치를 1개로 제한한다(`uk_settlement_batches_asset_in_progress` 유니크 인덱스, V11→V17) — 앞 회차가 `FAILED`/`PARTIAL_FAILED`로 멈추면 그 자산의 다음 정산(새 수익 배당 개시)이 열리지 않는다. 그런데 실패 알림은 `disburse()`가 회차를 `FAILED`로 확정하는 순간 `SettlementFailureNotifier`가 딱 한 번만 발송했고, 그 알림이 전달에 실패하거나 담당자가 놓치면 회차가 방치돼도 다시 알려주는 경로가 없었다. `RevenuePollingScheduler`가 자산이 차단된 상황 자체는 로그로 감지하고 있었지만, 그 감지가 재통보로 이어지지는 않았다.

**Task**

실패 알림이 한 번 유실돼도 시스템이 스스로 복구하도록, 미해결 회차를 주기적으로 재확인해 재통보하고, 방치 기간이 길어질수록 눈에 띄게 만드는 구조를 설계한다.

**Action**

1. `UnresolvedFailureReminderScheduler`를 신설해 1시간마다 `FAILED`/`PARTIAL_FAILED` 상태로 남은 배당(`SettlementBatchRepository`)·최종정산(`FinalSettlementBatchRepository`) 회차를 각각 전량 조회한다.
2. `SettlementFailureNotifier.remind()` — 최초 알림 발송 후 1시간(`INITIAL_ALERT_GRACE`) 유예를 두고, 그 이후에도 실패 상태면 날짜별 멱등키로 **하루 최대 1건**만 재통보해 알림 폭주를 막았다. 최초 알림 전달 자체가 실패했던 경우엔 유예 없이 바로 재발송한다.
3. 7일(`ESCALATE_AFTER_DAYS`)을 넘겨 방치되면 알림 제목을 `[장기 미해결] N일째 방치`로 바꿔, 오래된 건이 다른 알림 사이에 묻히지 않게 했다.
4. `RevenuePollingScheduler`가 자산 차단을 감지하면 "이 실패 때문에 대기 중인 수익 revenueId"를 실어 `remindUnresolvedDividendBatch(batch, revenue.revenueId())`를 호출하도록 연결 — 단순히 "회차가 실패했다"가 아니라 "이 실패로 다음 수익 배당까지 막혀 있다"는 구체적 맥락을 담아 재통보한다.
5. 알림은 analysis-service의 `SlackNotificationSender`를 거쳐 실제 Slack 채널로 전달되도록 기존 알림 경로(`analysisServiceClient.sendNotification`)를 그대로 재사용했다.
6. 이후 커밋(`fd8c192`)에서, 한 종류(배당)의 조회 장애가 다른 종류(최종정산)의 재통보 주기를 취소하지 않도록, 그리고 한 회차의 실패가 다른 회차의 재통보를 막지 않도록 조회·처리를 종류별·회차별로 격리했다.

**Result**

- 실패 알림이 "회차 실패 시점 1회성 이벤트"에서 "해결될 때까지 멈추지 않는 재확인 구조"로 전환됐다.
- 스케줄러가 `FAILED`/`PARTIAL_FAILED` 전체를 매시간 전량 스캔하는 구조라, 특정 회차만 누락되는 경우가 설계상 발생하지 않는다(스캔 커버리지 100%).
- 방치 기간에 비례해 알림 강도가 올라가는 에스컬레이션(7일 기준 제목 변경)을 확보했고, 동시에 하루 1건 멱등 처리로 같은 회차에 대한 알림 폭주는 막았다.

**Learn**

실패 자체를 없앨 수 없다면, 그다음 방어선은 "실패가 방치되는 것"을 막는 것이다. 알림을 1회성 이벤트로만 설계하면 그 한 번이 유실되는 순간 복구 경로가 사라진다 — 상태를 주기적으로 재확인하는 스케줄러가 있어야 사람의 주의력(알림을 놓치지 않는 것)에 기대지 않는 시스템이 된다.

---

## 같은 이벤트가 두 번 오거나 동시에 들어와도 정산 회차가 중복 생성되지 않도록: 회차 개시 멱등화

**Situation**

정산 회차 개시(`openBatchInternal`)는 두 경로에서 호출될 수 있다 — 기존부터 있던 `RevenuePollingScheduler`(주기적으로 asset-service의 준비된 수익 목록을 Feign으로 폴링해 회차를 여는 백스톱 경로)와, 새로 추가한 `RevenueReadyEventConsumer`/`AssetTerminationRequestedEventConsumer`(Kafka `RevenueReady`·`AssetTerminationRequested` 이벤트로 회차를 여는 경로)다. Kafka는 at-least-once 전달이라 같은 이벤트가 재전송될 수 있고, 두 경로가 같은 `revenueId`를 거의 동시에 처리하면 회차가 중복 생성될 위험이 있었다.

**Task**

회차 개시 로직을 멱등화해, 같은 `revenueId`로 다시 호출돼도 새 배치를 만들지 않고 기존 배치를 그대로 반환하게 하고, 두 경로가 동시에 들어와 경합하는 경우도 안전하게 처리한다.

**Action**

1. `openBatchAutomatically` 멱등화 — 회차를 만들기 전에 `findByRevenueIdAndIsDeletedFalse`로 기존 배치를 먼저 조회해, 있으면 예외 없이 그 배치를 그대로 반환하도록 했다.
2. 동시 삽입 경합 방어 — 두 요청이 동시에 "기존 배치 없음"을 보고 둘 다 저장을 시도해도, DB 유니크 제약(`uk_settlement_batches_revenue_id`)이 한쪽만 통과시킨다. 진 쪽은 `SETTLEMENT_ALREADY_EXISTS_FOR_REVENUE` 예외를 잡아, 이긴 쪽이 이미 만든 배치를 재조회해서 반환하도록 만들었다.
3. 점진적 전환 — 기존 `RevenuePollingScheduler`(폴링 백스톱) 경로는 그대로 둔 채 Kafka Consumer 경로를 추가해, 두 경로가 같은 멱등 로직 위에서 공존하게 했다. 전환 도중에도 서비스 중단이나 이벤트 유실 걱정 없이 안전하게 옮겨갈 수 있었다.
4. 단위 테스트로 검증 — `RevenueReadyEventConsumerTest`("기존 배치를 멱등 재사용해도(newlyCreated=false) 통보·지급은 그대로 호출하고 recovered_existing 메트릭을 남긴다"), `AssetTerminationRequestedEventConsumerTest`("기존 최종 정산 배치를 반환해도(newlyCreated=false) 지급을 그대로 호출한다 — 커밋 후 재기동/재전달되는 경우 지급이 누락되지 않아야 한다")로 재전달·재기동 상황을 그대로 재현해 검증했다.

**Result**

같은 이벤트 재전달·동시 삽입이 발생해도, 애플리케이션 조회(멱등 반환) + DB 유니크 제약(경합 방지) 이중 방어로 회차 중복 생성이 구조적으로 불가능해졌다. 기존 폴링 경로를 끄지 않고도 Kafka 경로로 무중단·점진 전환할 수 있었다.

**Learn**

분산 시스템에서 "정확히 한 번" 전달은 보장할 수 없다 — 대신 "여러 번 와도 결과가 같다(멱등)"를 보장하는 편이 현실적이다. 특히 새 경로(Kafka)를 기존 경로(폴링) 위에 얹을 때는, 둘이 동시에 존재하는 전환 기간 자체를 정상 상태로 놓고 설계해야 안전하게 전환할 수 있다.

**참고**
우체통을 하나 생각해볼게요.

문제 상황                                                                                                                                                                                                                        
"수익이 준비됐어요!"라는 편지(이벤트)가 정산 서비스한테 옵니다. 그런데 이 편지가 두 가지 경로로 올 수 있어요.
1. 정산 서비스가 직접 우체국에 "혹시 온 편지 있어요?" 하고 주기적으로 물어보러 가는 방법 (폴링)
2. 우체부가 편지가 생기자마자 바로 가져다주는 방법 (Kafka)

그런데 우체부(Kafka)는 가끔 "확실하게 전달됐는지" 확신이 안 서면 같은 편지를 두 번 배달하기도 해요(이게 일부러 그런 거예요 — 안 배달하는 것보다 두 번 배달하는 게 낫다고 정한 규칙이거든요). 문제는, 편지 한 장이 올 때마다 "정산
통장"을 새로 하나씩 만들도록 되어 있으면, 같은 수익에 대해 통장이 두 개 생겨버려요. 통장이 두 개면 배당금도 두 배로 계산될 수 있으니 큰일이죠.

해결한 방법
1. 편지를 받으면 통장을 만들기 전에 먼저 "어? 이 편지에 대한 통장 이미 있나?" 하고 확인해요. 이미 있으면 새로 안 만들고 원래 있던 통장을 그대로 돌려줘요.
2. 그런데 아주 운 나쁘게, 두 편지(폴링 쪽 하나, 우체부 쪽 하나)가 완전히 동시에 도착해서 둘 다 "통장이 없네? 그럼 내가 만들어야지"라고 동시에 착각할 수도 있어요. 이럴 땐 은행 시스템(데이터베이스)에 "같은 이름표로는 통장을 두
   개 못 만든다"는 특별 자물쇠를 걸어뒀어요. 그러면 둘 중 하나만 통장 만들기에 성공하고, 진 쪽은 실패하는 대신 "아, 이미 만들어졌구나" 하고 성공한 쪽 통장을 가져다 씀으로써 조용히 마무리돼요.
3. 옛날 방법(우체국에 물어보러 가기)을 갑자기 끄지 않고, 새 방법(우체부 배달)을 옆에 나란히 켜뒀어요. 둘 다 켜져 있어도 위 1번·2번 덕분에 통장이 중복되지 않으니까, 서비스를 잠깐도 끄지 않고 안전하게 새 방법으로 갈아탈 수     
   있었어요.
4. 이게 진짜로 잘 작동하는지, "같은 편지가 일부러 두 번 온 것처럼" 만들어서 테스트도 해봤어요.

결과                                                                                                                                                                                                                             
편지가 몇 번을 다시 오든, 두 통이 동시에 오든, 정산 통장은 절대 두 개가 생기지 않는다는 걸 코드와 데이터베이스 두 겹으로 확실하게 보장하게 됐어요.

---

## 조회 비용이 "예측 불가능"해지는 문제를 전용 인덱스로 없애다: /dividends/me 성능 개선

**Situation**

`GET /api/v1/dividends/me`는 투자자 본인의 배당 내역만 조회하는 API다. 그런데 `p_dividend_payouts`에 `investor_id`가 걸린 유일한 인덱스는 `(settlement_batch_id, investor_id)` 복합 유니크 인덱스뿐이고, `investor_id`가 두 번째 컬럼이라 직접 탐색이 안 됐다. MVP 시점엔 정산 회차가 몇 개뿐이라 문제가 드러나지 않았지만, 회차 수는 서비스 운영 기간에 비례해 계속 쌓이는 구조였다. PostgreSQL의 B-tree skip scan 덕에 인덱스가 아예 안 쓰이는 건 아니었지만, 선행 컬럼(`settlement_batch_id`)의 distinct 값마다 내부적으로 반복 탐색이 필요했다.

**Task**

이 조회 비용이 회차 수와 정확히 어떤 관계인지 실측으로 확인하고, 필요하면 개선한다. 단순 Before/After 1회 비교로는 "회차 수 때문"이라는 인과관계를 증명할 수 없으므로, 회차 수만 변수로 두는 통제 실험으로 검증한다.

**Action**

1. 회차 수를 500 / 2,000 / 5,000으로 바꿔가며, payout 총 행수(100만)·투자자당 배당 건수(100건)는 고정한 통제 실험을 설계했다.
2. `EXPLAIN (ANALYZE, BUFFERS)`를 1차 증거로 채택했다 — 응답시간은 캐시 상태에 따라 흔들려 반박 여지가 있지만, `Buffers`(읽은 페이지 수)는 흔들리지 않는다는 것을 인프라를 완전히 재구축한 뒤 재측정하는 재현성 테스트로 직접 확인했다.
3. 실측 결과, 비용이 회차 수에 선형 비례하지도 않았다 — 500→2,000에서는 `Index Searches`가 1,001→3,984로 거의 정확히 4배 늘었지만, 2,000→5,000에서는 오히려 3,984→3,031로 줄었다. "지금 얼마나 느린가"가 아니라 **"앞으로 얼마나 느려질지 아무도 예측할 수 없다"**는 게 진짜 문제라고 판단했다.
4. `investor_id`를 선행 컬럼으로 둔 전용 부분 인덱스를 추가했다:
   ```sql
   CREATE INDEX IF NOT EXISTS idx_dividend_payouts_investor
       ON p_dividend_payouts (investor_id, updated_at DESC, dividend_payout_id ASC)
       WHERE is_deleted = false;
   ```
   조회 정렬(`ORDER BY updated_at DESC, id ASC`)까지 컬럼 순서·방향에 그대로 반영해 `Sort` 노드까지 함께 제거했다 — 방향 하나라도 어긋나면 첫 번째 정렬 키는 맞아도 두 번째 키가 어긋나 `Sort`가 되살아난다.
5. 읽기 성능만 보고 채택하지 않고, 회차 개시 시 payout 대량 INSERT에 미치는 쓰기 비용도 별도로 측정했다 — `CHECKPOINT`+`VACUUM`으로 캐시·체크포인트 타이밍을 통제한 뒤, 약 40만 행 규모 합성 데이터셋에서 `EXPLAIN (ANALYZE, BUFFERS, WAL)`로 Before/After 각 10회씩 WAL 생성량을 비교했다(별도 실험, 아래 부록 참고).

**Result**

읽기 개선 — 회차 수와 완전히 무관해짐:

| 회차 수 | Before Index Searches | After | Before Buffers | After |
|---|---|---|---|---|
| 500 | 1,001 | 1 | 3,244 | 83 |
| 2,000 | 3,984 | 1 | 12,671 | 83 |
| 5,000 | 3,031 | 1 | 15,059 | 83 |

After는 회차 수 500/2,000/5,000 세 경우 모두 `Buffers` 83으로 완전히 동일하다 — 회차가 10배 쌓여도 조회 비용이 조금도 늘지 않는다는 것을 확인했다.

HTTP 부하 재현(회차 5,000, 30 threads·180초)에서도 동일하게 개선을 확인했다:

| 지표 | Before | After | 개선폭 |
|---|---|---|---|
| 처리량 | 38.4 req/s | 583.6 req/s | 15.2배 |
| p95 | 1,930ms | 112ms | 17.2배 |
| p99 | 4,740ms | 200ms | 23.7배 |

쓰기 비용 — 별도 실험으로 측정(약 40만 행 규모 합성 데이터셋에서 payout 1만 건 INSERT를 롤백하며 WAL 생성량만 비교, 절차는 아래 부록):

| 지표 | Before(인덱스 없음, 10회 평균) | After(인덱스 있음, 안정구간 9회 평균) | 변화 |
|---|---|---|---|
| WAL records | 50,351 | 60,350 | +9,999건(삽입 행 1건당 인덱스 엔트리 1개, 예측과 정확히 일치) |
| WAL bytes | 7,331,289 | 8,959,226 | +22.2% |

인덱스 생성 직후 첫 배치는 해당 인덱스에 대한 첫 실제 쓰기라 WAL bytes가 일시적으로 3배 이상 튀는 현상(28,791,334 bytes)을 확인했다 — 재현 가능한 현상으로 별도 기록하고 steady-state 비용 계산에서는 제외했다.

**결론**: 읽기 처리량 15.2배 개선 vs 쓰기 비용 22.2% 증가로, 트레이드오프가 비대칭적으로 유리함을 수치로 확인했다. (읽기 실험의 174MB/100만 행 데이터셋과 이어지는 후속 측정이 아니라, 같은 인덱스에 대한 별도의 독립적 실험이다.)

**Learn**

응답시간만으로 인덱스 효과를 주장하면 캐시 상태에 따라 반박당할 수 있다 — `EXPLAIN (ANALYZE, BUFFERS)`처럼 흔들리지 않는 지표를 1차 증거로 삼고, 인프라를 통째로 재구축해도 재현되는지까지 확인해야 신뢰할 수 있는 숫자가 된다. 또한 읽기 개선만 보고 인덱스를 채택하지 않고, 그 인덱스가 만드는 쓰기 비용까지 별도로 실측해 트레이드오프 전체를 숫자로 보여줘야 설득력이 생긴다.

---

## 정산 저장 트랜잭션 타임아웃(20초) 재측정 설계 (진행 중, 배포 서버)

### 배경 — 보류됐던 이슈

`SettlementBatchWriter.persist()`(회차 저장: `SettlementBatch` + `HoldingSnapshot` + payout 최대 1만 건을 한 트랜잭션에 묶어 저장)는 `@Transactional(propagation = REQUIRES_NEW, timeout = 20)`로 걸려 있다. 이 20초는 원래 "저장" 구간 내부에 찍은 진단 로그로 잰 **14.2~16.7초**에 마진을 더해 정한 값이었는데, 이후 그 로그 자체가 실제 flush/commit 이전(엔티티를 영속성 컨텍스트에 큐잉만 한 시점)에 찍히고 있었다는 "측정 버그"가 드러났다(`docs/localTest.md` "측정 버그 발견 및 정정" 절). 재측정은 당시 비용 대비 효용이 낮다고 판단해 보류했고, 20초는 "근거가 불확실해졌지만 부족하면 `TransactionException`으로 즉시 드러나 위험은 낮다"는 전제로 그대로 두기로 했었다.

### 재조사 — 지금 다시 재는 게 안전한가

재측정을 실제로 추진하기 전에, 로그 위치 수정 커밋(`1e3fbe5 정산 회차 개시 로그 위치 변경`)이 지금 로드테스트 배포 서버(`loadtest-b-after-kafka2`, `996a80c`)에 반영돼 있는지부터 확인했다 — 반영 안 된 서버에서 측정하면 예전과 똑같은 버그를 반복하게 된다.

- `git merge-base --is-ancestor`로 확인한 결과, 해당 커밋은 배포 서버 브랜치에 **이미 포함돼 있었다**. `persist 완료` 로그는 `SettlementBatchWriter` 내부가 아니라 `SettlementCommandService`로 옮겨져 있어, `persist()`가 반환된(=커밋이 끝난) 이후 시점을 찍는다.
- 배포 서버 브랜치와 최신 브랜치(`settlement-mvp`) 사이의 `settlement-service` 전체 diff를 직접 비교해, 이 측정에 영향을 줄 만한 차이(DB 풀 크기, `hibernate.jdbc.batch_size`, 트랜잭션/타임아웃 설정)가 **없음**을 확인했다. 차이는 전부 이 트랜잭션과 무관한 것들(지갑 서킷브레이커/Feign 타임아웃, 최종 정산 DEAD_LETTER 세분화 등)이었다.
- 다만 `settlement-mvp`의 `docker-compose.yml`에는 postgres에 `pg_stat_statements`가 추가돼 있어, 앱 로그 타이밍 하나에만 의존하지 않고 DB 쪽 독립 지표로 교차검증할 수 있는 수단이 하나 더 생겼다 — 이걸 쓰려면 최신 코드로 재배포하는 게 낫다고 판단했다.

### 측정 설계

**진단 로그를 3줄에서 5줄로 늘렸다** — 기존 3줄(A/B/C)만으로는 "홀딩스 페이징 시간"이 측정이 아니라 총 소요시간에서 역산한 값이었고, "저장"(C−B) 안에서 flush와 커밋을 구분할 수 없었다. 두 줄을 코드에 추가했다(`SettlementCommandService.java`, `SettlementBatchWriter.java`에 반영 완료):
- `SettlementCommandService.openBatchInternal()`에 **A0** 추가: `fetchAndValidateHoldingsSnapshot` 호출 **직전**에 `log.info("[진단]holdings 페이징 시작 ...")`.
- `SettlementBatchWriter.persist()`에 **D** 추가: `dividendPayoutRepository.saveAll(payouts)` 바로 뒤에 `dividendPayoutRepository.flush()`(명시적 flush, `JpaRepository`가 제공하는 메서드)를 호출하고 `log.info("[진단]persist flush 완료 — 커밋 대기 ...")`.

```
A0: [진단]holdings 페이징 시작                      ← fetchAndValidateHoldingsSnapshot() 호출 직전 (SettlementCommandService)
A : [진단]holdings 페이징 완료, persist 호출 시작    ← persist() 호출 직전 (SettlementCommandService)
B : [진단]persist 진입 — 커넥션 획득 완료            ← persist() 진입 (SettlementBatchWriter) — "획득 완료"라는 문구는 아래 로컬 실험으로 검증
D : [진단]persist flush 완료 — 커밋 대기             ← saveAll()+flush() 직후, 커밋 전 (SettlementBatchWriter, 신규)
C : [진단]persist 완료 — 저장 종료                   ← persist() 반환 후(=커밋 완료 후) (SettlementCommandService)
```

- **A0→A = "홀딩스 페이징"** — 직접 측정된다.
- **A→B = "대기"(가칭)** — B가 실제로 커넥션 획득 시점인지는 아래 "시계 질문" 절에서 확인한다.
- **B→D = "flush"** — payout 1만 건의 영속성 컨텍스트 flush·dirty-checking·실제 INSERT 실행.
- **D→C = "커밋"** — WAL fsync를 포함한 커밋 자체 비용. `pg_stat_statements`의 `COMMIT` 행과 직접 비교할 구간.

⚠️ **남은 미확정 사항 하나**: "대기"가 정말 timeout=20의 시계 안에 있는지 — 아래 로컬 미니 실험으로 확인한다(EC2 불필요).

측정은 다음을 지킨다:
1. **독립적인 두 지표로 교차검증한다** — 앱 로그와 `pg_stat_statements`(`total_exec_time`)가 같은 자릿수로 나오는지 확인한다. 둘의 차이를 "네트워크 왕복"으로 단정하지 않는다 — `pg_stat_statements.calls`는 **서버 측 실행 횟수**일 뿐 클라이언트 왕복 횟수가 아니다. "DB 밖" 시간은 B→D(flush) 구간을 직접 재서 확인한다.
2. **평온한 상태뿐 아니라 혼잡한 상태에서도 잰다 — 각 3회(총 6회).** 이 항목의 가치는 "혼잡 조건에서의 분포"이므로 n=1~2로는 최댓값 자체가 의미를 잃는다. 반대로 5+5는 EC2 자산 예산·시간 대비 얻는 신뢰도 증가가 작다 — 3+3을 최소선으로 잡는다.
3. **평온/혼잡을 블록으로 몰아서 하지 않고 번갈아 실행한다** — 매 트리거마다 payout 1만 건이 쌓여 인덱스도 커지므로, 혼잡 효과와 데이터 누적 효과를 분리하기 위해서다.
4. **CPU 크레딧·스케줄러 소음 추적은 상시 수행하지 않는다** — 값이 눈에 띄게 튀는 트리거가 나왔을 때만 원인 후보로 확인한다(아래 "값이 이상하면" 참고). 매번 확인하는 건 이 실험의 본질(timeout 값 산출)과 무관한 비용이다.

### 시계 질문 — 로컬 미니 실험 (EC2·재배포 불필요, 약 5분)

"대기가 timeout=20의 시계에 포함되는가"는 **로컬에서 풀을 최소치로, timeout을 임시로 낮춰 일부러 터뜨려보는 게 가장 싼 확인이다** — 소스만 읽고 "Spring 문서상 이렇다"로 끝내면 포트폴리오에서 "검증했다"가 아니라 "읽었다"가 된다.

⚠️ **실측으로 드러난 사실 — 풀=1은 쓸 수 없다.** 처음 `DB_POOL_MAX_SIZE=1`로 재배포했더니 애플리케이션이 기동조차 못 하고 계속 재시작했다. 로그 원인:
```
HikariPool-1 - Connection is not available, request timed out after 30229ms (total=1, active=1, idle=0, waiting=0)
Caused by: org.flywaydb.core.internal.exception.FlywaySqlException ...
```
Flyway가 마이그레이션 중 advisory lock용 커넥션과 별개로 커넥션을 하나 더 필요로 하는 경우가 있어서, 풀이 정확히 1개면 **Flyway가 기동 시점에 자기 자신과 데드락**한다. 그래서 **풀은 2로, 동시 트리거는 3개로** 설계를 바꿨다 — 자산 1(5만 명)이 먼저 1자리를 쥐고, 남은 1자리를 자산 2·3(둘 다 소규모)이 동시에 다투게 하면 둘 중 하나는 반드시 기다린다.

**이 실험이 판별되려면 "대기 > timeout > 저장 단독"이 성립해야 한다** — 자산 1(5만 명)이 쥔 커넥션 점유 시간(= 자산 1의 "저장")이 timeout(3초)보다 길어야 하고, 자산 2·3 중 못 받은 쪽이 그 안에 persist()에 진입해 기다려야 한다. 아래 "판정"의 마지막 항목(자산 1의 저장이 3초를 넘었는지 확인)이 이 조건을 사후에 검증하는 단계다. **로컬 실험의 결과는 "시계가 무엇을 재는가"에만 쓰고, timeout 값 산출(최종 n=6 계산)에는 쓰지 않는다** — 로컬과 EC2는 하드웨어가 다르므로 섞으면 근거가 흐려진다. 이 한 줄을 측정 환경표에도 적어둔다.

**준비** (완료 — 아래는 실제로 실행해서 확인한 상태)
1. 로컬 스택 기동: `docker compose --env-file .env -f infrastructure/docker-compose.yml up -d`. ✅
2. `SettlementBatchWriter.java`의 `timeout = 20`을 **로컬에서만** `timeout = 3`으로 변경(`// 로컬 시계 실험 전용, 실험 후 git checkout으로 원복` 주석 포함). 커밋 안 함. ✅
3. `.env`에 `DB_POOL_MAX_SIZE=2`(처음엔 1로 했다가 위 Flyway 데드락으로 2로 수정) 추가 후 재빌드·재기동. 컨테이너 안 `DB_POOL_MAX_SIZE=2`, `curl .../actuator/health` → `{"status":"UP"}` 확인됨. ✅
4. 자산 3개를 시딩(`docs/seed-settlement-timeout-assets.sql`을 세 번 호출 — `ON CONFLICT DO NOTHING`이라 이미 있는 자산·보유 투자자는 재실행해도 안 늘어남):
   ```bash
   docker compose --env-file .env -f infrastructure/docker-compose.yml exec -T postgres \
     psql -U moneytown -d asset_db -v asset_count=1 -v investor_count=50000 -f - < docs/seed-settlement-timeout-assets.sql
   docker compose --env-file .env -f infrastructure/docker-compose.yml exec -T postgres \
     psql -U moneytown -d asset_db -v asset_count=2 -v investor_count=100 -f - < docs/seed-settlement-timeout-assets.sql
   docker compose --env-file .env -f infrastructure/docker-compose.yml exec -T postgres \
     psql -U moneytown -d asset_db -v asset_count=3 -v investor_count=100 -f - < docs/seed-settlement-timeout-assets.sql
   ```
   자산 1(투자자 5만 명)은 "저장"이 3초를 확실히 넘도록 일부러 크게, 자산 2·3(투자자 100명씩)은 풀의 남은 1자리를 서로 다투게만 하면 되므로 작게 잡았다. 실측 결과 `p_holdings` 건수는 자산 1=50,000 / 자산 2=100 / 자산 3=100으로 의도대로 분리됐다.

**실행** (아래부터는 사용자가 직접 커맨드를 입력해 실행) — ⚠️ 이전 실행의 로그가 쌓여 있으면 `grep -q "persist 진입"`이 **그 이전 기록에 바로 걸려** 대기 없이 바로 나갈 수 있다(여러 자산이 같은 메시지를 찍어 batchId로도 구분이 안 됨). 실험 시작 시각을 기록해 `--since`로 그 이후 로그만 보게 한다.
```bash
START_TS=$(date -u +%Y-%m-%dT%H:%M:%S)

# 자산 1(큰 것) 먼저 트리거 — 백그라운드. 풀 2자리 중 1자리를 이 트랜잭션이 쥔다.
curl -s -X POST http://localhost:19097/api/v1/settlements \
  -H "Content-Type: application/json" -H "X-User-Role: ADMIN" \
  -H "X-User-Id: 11111111-1111-1111-1111-111111111111" \
  -d '{"assetId":"<asset_1>","revenueId":"<revenue_1>"}' &

# 자산 1이 persist()에 진입(=커넥션 1자리를 쥠)할 때까지 대기 — START_TS 이후 로그만 본다
until docker compose --env-file .env -f infrastructure/docker-compose.yml logs --since "$START_TS" settlement-service \
  | grep -q "진단\]persist 진입"; do sleep 0.2; done

# 자산 2·3(작은 것 둘) 동시 트리거 — 남은 1자리를 서로 다툰다. 하나는 바로 받고 하나는 기다린다.
curl -s -X POST http://localhost:19097/api/v1/settlements \
  -H "Content-Type: application/json" -H "X-User-Role: ADMIN" \
  -H "X-User-Id: 22222222-2222-2222-2222-222222222222" \
  -d '{"assetId":"<asset_2>","revenueId":"<revenue_2>"}' &
curl -s -X POST http://localhost:19097/api/v1/settlements \
  -H "Content-Type: application/json" -H "X-User-Role: ADMIN" \
  -H "X-User-Id: 33333333-3333-3333-3333-333333333333" \
  -d '{"assetId":"<asset_3>","revenueId":"<revenue_3>"}' &
wait

docker compose --env-file .env -f infrastructure/docker-compose.yml logs --no-color --timestamps --since "$START_TS" settlement-service \
  | grep -E "진단\]|TransactionTimedOutException|TransactionSystemException"
```

**판정** — batchId로 자산 2·3 중 **대기가 생긴 쪽**(A와 B 사이 시간 간격이 뚜렷한 쪽)의 로그를 본다:
- **A는 찍혔는데 B가 안 찍힌 채로 예외**: 시계가 "대기"를 포함한다 — timeout과 비교할 값은 **A→C**다.
- **B는 찍혔는데 D 전에 예외**: 시계가 대기를 포함하지 않고 B에서 시작한다 — timeout과 비교할 값은 **B→C**다.
- **자산 1(큰 것)의 A0~C 로그로 "저장"이 실제로 3초를 넘었는지**도 같이 확인한다 — 안 넘었으면 자산 1 규모를 더 키워 재시도한다(이 실험 자체도 "최악 쪽에서 실제로 쟀다"는 근거가 된다).

### 로컬 시계 실험 — 결과 (실측 완료, 2026-10-05)

| 자산 | A0→A(홀딩스 페이징) | A→B(대기) | B 이후 | 결과 |
|---|---|---|---|---|
| 1(5만 명) | 99.34s | 0.005s | B+3.09s에 `TransactionException: transaction timeout expired` | 실패(롤백) |
| 2(100명) | 0.19s | **30.003s** | B+0.439s(flush)+0.031s(commit) | **성공**(예외 없음) |
| 3(100명) | 0.16s | B를 못 찍음, 30.18s 후 `CannotGetJdbcConnectionException` | — | 실패(커넥션 자체를 못 받음) |

**결론: 시계는 "대기"를 포함하지 않는다 — 기준값은 B→C로 확정한다.** 자산 2가 정확히 HikariCP `connection-timeout`(기본값 30,000ms)만큼 대기했다가 커넥션을 받은 뒤, `timeout=3`인 상태로 flush+commit을 아무 예외 없이 마쳤다 — 대기(30.003s)+저장(0.47s)을 합친 총 소요(30.47s)가 3초를 몇 배나 넘었는데도 안 터졌으므로, 시계가 대기를 포함했다면 나올 수 없는 결과다. 반례가 성립할 수 없는 구조라 추가 라운드 없이 이걸로 확정한다.

부수적으로 확인된 것:
- 자산 1의 실패 시점(B+3.09s)이 곧 "`payout 5만 건 flush`가 3초를 실제로 넘는다"는 직접 증거다(추정이 아니라 Hibernate의 statement timeout이 정확히 그 경계에서 끊어준 것).
- 자산 3의 실패는 `persist()`의 `timeout=3`이 아니라 HikariCP 자체의 `connection-timeout`(30s, 별개 메커니즘)이다 — 같은 시간대에 백그라운드 스케줄러 스레드(`scheduling-1`)도 같은 예외를 겪어, 풀=2가 스케줄러와도 경쟁할 만큼 작았다는 걸 보여준다. 이건 본 질문(시계 포함 여부)과 무관한 노이즈다.
- `p_settlement_batches`에 자산 1·3의 행이 0건 — 두 실패 모두 `REQUIRES_NEW` 트랜잭션이 깨끗하게 롤백됐음을 확인(부분 커밋 없음).

**원복 완료**: `git checkout`으로 `SettlementBatchWriter.java`를 되돌리고(`timeout=20` 복원), `.env`의 `DB_POOL_MAX_SIZE` 줄을 삭제한 뒤 재빌드·재기동해 `DB_POOL_MAX_SIZE=15`·`{"status":"UP"}`로 확인했다. 이 실험은 EC2를 전혀 건드리지 않았으므로 Phase 1에 영향이 없다.

### Phase 1 — 본 실험 실행 절차 (EC2, 명령어)

**1) `settlement-mvp`로 재빌드·재배포** — `pg_stat_statements`는 `shared_preload_libraries`라 postgres 재시작이 있어야 반영된다(6개 서비스 DB가 공유하는 인스턴스라 짧은 순단 발생, 테스트 서버라 무방). A0/D 진단 로그, `metrics` actuator 노출도 이 재배포로 같이 반영된다.
```bash
./gradlew :settlement-service:bootJar
docker compose --env-file .env -f infrastructure/docker-compose.yml build settlement-service
docker compose --env-file .env -f infrastructure/docker-compose.yml up -d postgres
docker compose --env-file .env -f infrastructure/docker-compose.yml up -d settlement-service
```

**1-1) 수익 폴링이 꺼진 채로 재배포됐는지 재확인** — `.env`의 `SETTLEMENT_REVENUE_POLLING_ENABLED=false`(`settlement.scheduler.revenue-polling.enabled` 프로퍼티, `RevenuePollingScheduler`를 `@ConditionalOnProperty`로 끄는 값)는 postgres를 포함한 재배포 과정에서 `.env` 자체가 덮어써지는 실수가 있어도 컨테이너 안에서는 안 보인다. 이전에 이 값이 안 꺼져 있어서 3분 폴러가 시드해둔 READY 수익을 먼저 가져가 트리거가 전부 409로 실패했던 적이 있었으므로, 재배포 직후 반드시 컨테이너 내부 값으로 확인한다.
```bash
docker compose --env-file .env -f infrastructure/docker-compose.yml exec settlement-service env | grep SETTLEMENT_REVENUE_POLLING
```
`SETTLEMENT_REVENUE_POLLING_ENABLED=false`가 안 나오면(값이 없거나 `true`면) `.env` 마지막 줄에 개행 없이 이어붙어 조용히 무시된 경우가 흔하다 — `.env`를 직접 열어 확인 후 재기동한다.

**1-2) 자산·수익 사전 시딩** — 정산은 같은 자산·수익으로 회차를 다시 열 수 없다(`uk_settlement_batches_revenue_id`, `uk_settlement_batches_asset_in_progress` — asset당 `FAILED`/`PARTIAL_FAILED`/`DISBURSING` 등 "진행 중" 배치가 있으면 새 배치를 못 연다). 평온 3회 + 혼잡 3회(아래 6번) + 여유 몇 개를 위해 **자산 10개**를 미리 만든다(실행 중간에 바닥나서 409가 뜨고 재시딩으로 측정 조건이 깨지는 걸 막으려는 목적).

`seed-settlement-index-cost.sql`은 이 용도로 못 쓴다 — 그건 `settlement_db`에 SQL로 직접 꽂는 스크립트라 애플리케이션의 asset-service Feign 왕복(`fetchAndValidateRevenue`/`fetchAndValidateHoldingsSnapshot`) 경로를 거치지 않는다. 대신 전용 스크립트(`docs/seed-settlement-timeout-assets.sql`)로 `asset_db`에 자산 10개 + 자산당 투자자 1만 명 holdings + READY 수익 1건씩을 만든다.
```bash
docker compose --env-file .env -f infrastructure/docker-compose.yml exec -T postgres \
  psql -U moneytown -d asset_db -v asset_count=10 -v investor_count=10000 -f - \
  < docs/seed-settlement-timeout-assets.sql
```
마지막 `SELECT`가 출력하는 `(asset_id, revenue_id)` 10쌍 중 1~3을 평온, 4~6을 혼잡, 7~10을 실패 시 여유분으로 쓴다(재사용하지 않음).

**1-3) `pg_stat_statements` extension이 실제로 생성됐는지 확인** — `docker-compose.yml`의 `shared_preload_libraries=pg_stat_statements`는 postgres 재시작 시에만 반영되는 **서버 설정**이고, 실제로 쿼리를 걸 수 있는 `pg_stat_statements` 뷰는 **DB별로 `CREATE EXTENSION`을 따로 해야 생긴다**. `infrastructure/postgres/init.sql`이 이 extension을 만들어주지만 **그건 `postgres` DB에 한 번, 그리고 볼륨이 새로 만들어질 때(`docker-entrypoint-initdb.d`)만** 실행된다 — 기존 볼륨을 그대로 쓰고 있다면 1)번에서 postgres를 재시작해도 extension 자체는 없을 수 있다. 1)번 재시작 후 이걸로 확인·생성한다.
```bash
docker compose --env-file .env -f infrastructure/docker-compose.yml exec -T postgres \
  psql -U moneytown -d settlement_db -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements;"
```

**2) 측정 구간 직전 `pg_stat_statements` 초기화**
```bash
docker compose --env-file .env -f infrastructure/docker-compose.yml exec -T postgres \
  psql -U moneytown -d settlement_db -c "SELECT pg_stat_statements_reset();"
```

**3) `POST /settlements` 트리거** — 자산 1개당 투자자 1만 명 이상 규모로, 한 번에 회차 1개씩만 호출(로그 A/B/C 쌍이 섞이지 않도록). `SettlementCommandController.openSettlementBatch`는 `X-User-Role`만 `@RequestHeader`로 받고 `X-User-Id`는 요구하지 않는다(코드로 확인 — 없어도 400이 나지 않는다). 다만 혹시 다른 경로에서 막히는 경우를 대비해 방어적으로 같이 보낸다.
```bash
curl -X POST http://localhost:19097/api/v1/settlements \
  -H "Content-Type: application/json" \
  -H "X-User-Role: ADMIN" \
  -H "X-User-Id: 11111111-1111-1111-1111-111111111111" \
  -d '{"assetId":"<assetId>","revenueId":"<READY 상태 revenueId>"}'
```

**4) 로그에서 A0/A/B/D/C 타임스탬프 추출** — 같은 트리거 안에서 순서대로 5줄이 나온다.
```bash
docker compose --env-file .env -f infrastructure/docker-compose.yml logs --no-color --timestamps settlement-service \
  | grep -E "진단\]holdings 페이징 시작|진단\]holdings 페이징 완료|진단\]persist 진입|진단\]persist flush 완료|진단\]persist 완료" | tail -30
```
각 줄 맨 앞 타임스탬프로 네 구간을 계산한다: **홀딩스 페이징**(A−A0), **대기**(B−A), **flush**(D−B), **커밋**(C−D). "저장"(기존 C−B)은 flush+커밋(D−B + C−D)으로 쪼개서 본다.

**5) `pg_stat_statements`로 교차검증** — 혼잡 라운드(6번)에서는 JMeter가 `GET /dividends/me`를 초당 수십~수백 번 쏘는데, 그 SELECT도 `p_dividend_payouts`를 참조하므로 `%p_dividend_payouts%` 같은 느슨한 `ILIKE` 조건에 같이 걸려 INSERT와 SELECT가 섞여 나온다. `INSERT INTO ...%`로 앞을 고정해 SELECT가 원천적으로 안 걸리게 한다. ⚠️ **테이블명은 `p_holdings_snapshots`(holdings, 복수)다** — `HoldingSnapshot` 엔티티의 `@Table(name = "p_holdings_snapshots")`로 코드에서 직접 확인했다. `p_holding_snapshots`(단수)로 쓰면 그 INSERT는 하나도 안 잡힌다.
```bash
docker compose --env-file .env -f infrastructure/docker-compose.yml exec -T postgres \
  psql -U moneytown -d settlement_db -c \
  "SELECT query, calls, mean_exec_time, max_exec_time, total_exec_time, rows
   FROM pg_stat_statements
   WHERE query ILIKE 'INSERT INTO p_dividend_payouts%'
      OR query ILIKE 'INSERT INTO p_settlement_batches%'
      OR query ILIKE 'INSERT INTO p_holdings_snapshots%'
      OR query = 'COMMIT'
   ORDER BY max_exec_time DESC;"
```
**비교 대상은 `mean_exec_time`이 아니라 `total_exec_time`(또는 `calls × mean_exec_time`)이다.** JDBC 데이터소스에 `reWriteBatchedInserts`를 켜두지 않았다(코드로 확인 — 어디에도 설정돼 있지 않다), 그래서 `hibernate.jdbc.batch_size=100`이 있어도 PgJDBC는 payout 1만 건을 1만 번의 개별 INSERT로 보낸다. 즉 `calls`가 약 10,000, `mean_exec_time`은 1ms 미만으로 잡히는 게 정상이고, 이 `mean`을 초 단위인 C−B와 비교하면 항상 안 맞는 것처럼 보인다. `total_exec_time`(모든 호출의 실행 시간 합)을 C−B와 비교해야 한다.
- `total_exec_time`이 C−B에 가까우면: 시간이 **DB 실행 자체**에 쓰인 것(병목이 DB 안).
- `total_exec_time`이 C−B보다 뚜렷이 작으면: 시간이 **DB 밖**(네트워크 왕복, flush, JVM)에서 쓰인 것. C−B에는 왕복·flush·커밋까지 포함되므로 DB 실행 합계보다 작게 나오는 게 오히려 정상이며, 이 자체가 "시간이 어디서 쓰였는가"를 보여주는 유의미한 결과다.
- `COMMIT` 행은 WAL fsync를 포함한 커밋 자체의 비용을 보여준다(PgJDBC가 `COMMIT`을 별도 쿼리로 보내 `track=top` 설정에서도 잡힌다).

**6) 평온/혼잡을 3라운드로 번갈아 실행** — "평온만 몰아서 끝내고 혼잡으로 넘어간다"는 하지 않는다(측정 설계 3번 — 데이터 누적 효과와 혼잡 효과가 섞인다). **라운드 r = 1..3**을 돌며 라운드마다 "평온 1회 → 혼잡 1회"를 짝지어 수행한다 — 평온 3회 + 혼잡 3회(총 6회)를 확보하면서 누적 효과가 평온/혼잡 양쪽에 고르게 섞이게 한다.

라운드 r마다:
1. (평온 확인 — 아래 박스) 통과 후 2)~5)를 1회 트리거 → `quiet-r`로 기록.
2. JMeter(아래 "JMeter 동시조회 부하 플랜") 시작, 60초 대기(안정화).
3. 부하가 도는 중에 2)~5)를 1회 트리거 → `congested-r`로 기록.
4. JMeter 정지, pending/active가 다시 0으로 돌아올 때까지(30초~1분) 대기.
5. 다음 라운드로.

**각 트리거에 `(quiet|congested)-r` 순번을 매겨 기록**해두고, 3라운드가 끝나면 "저장" 값을 라운드 순서대로 늘어놓아 **단조 증가하는지 확인**한다 — 평온·혼잡 양쪽이 같이 단조 증가한다면 혼잡이 아니라 데이터 누적(인덱스 커짐)이 원인일 가능성이 높다는 신호다. 한 트리거의 `C`(persist 완료) 로그가 찍힌 걸 확인한 뒤에 다음 트리거를 친다.

```bash
for i in 1 2 3; do
  curl -s http://localhost:19097/actuator/metrics/hikaricp.connections.pending | jq '.measurements'
  curl -s http://localhost:19097/actuator/metrics/hikaricp.connections.active | jq '.measurements'
  sleep 2
done
```
pending/active가 **연속 2~3회** 모두 0이면 평온 상태로 간주한다. 한 번만 보면 마침 비는 순간을 잘못 "평온"으로 오인할 수 있다.

위 1번(평온 확인)과 2~3번("60초 안정화 후 혼잡 중 트리거")의 근거는 시나리오 A가 TG-2를 TG-1보다 60초 먼저 투입한 것과 같다 — "부하가 걸리는 과도기"가 아니라 "이미 경쟁 중인 평형 상태"를 보고 싶은 것이다. 사정상 6회를 못 채우면 "표본 수가 n=⟨실제 횟수⟩"라고 결과에 명시한다(모르는 걸 안다고 하지 않는다).

**값이 이상하면 (상시 수행하지 않는 보조 확인)** — 6회 중 특정 트리거의 "저장" 값이 유독 튀면, 그때만 원인을 좁힌다:
- `POST /settlements` 직후 `dividendDisbursementService.disburseAsync()`가 claim+Outbox 저장과 Kafka 발행을 바로 시작한다. wallet_db를 이번 실험에서 시딩하지 않아 지급이 실패·재시도할 수 있고(`DisbursementRetryScheduler`·`UnresolvedFailureReminderScheduler`는 끄는 스위치가 없음, 코드로 확인), 이게 다음 트리거와 겹치면 소음이 된다. 의심되면 그 트리거의 `[A0, C]` 구간에 `재트리거합니다`/`재통보 점검` 로그가 끼었는지 grep해서 확인한다.
- CPU 크레딧 소진(`t3a.large` 등 버스터블 인스턴스)이 의심되면 그때 CloudWatch `CPUCreditBalance`를 확인한다.
- B 로그 위치가 의심되면(이례적으로 대기만 유독 커 보이면) `/actuator/metrics/hikaricp.connections.acquire`의 `MAX`를 그 트리거 전후로 떠서 대조한다(로컬 미니 실험과 같은 방법).

### JMeter 동시조회 부하 플랜 — 어디를 클릭하나

> GUI 실행은 JMeter 자체가 CPU를 먹으므로 플랜 작성·디버그에만 쓰고, 실측은 `-n`(비GUI)로 돌린다. `GET /dividends/me`는 게이트웨이의 JWT 검증 없이 settlement-service 포트(19097)로 직접 치면 `X-User-Role`/`X-User-Id` 헤더만으로 통과하므로, 실제 로그인·토큰 발급 없이 바로 플랜을 만들 수 있다. **가능하면 JMeter는 EC2가 아닌 다른 머신(본인 노트북 등)에서 돌린다** — 앱·DB가 떠 있는 EC2가 2vCPU 같은 소형 인스턴스라면 JMeter 자체가 그 CPU를 나눠 먹어 "DB/앱이 혼잡"이 아니라 "JMeter가 CPU를 뺏어가서" 느려진 것과 구분이 안 된다. 같은 머신에서 돌릴 수밖에 없다면 이 사실을 결과에 명시한다.

1. **CSV 준비(실제 존재하는 투자자 ID로 로테이션)**: `uuidgen`으로 아무 UUID나 생성하면 `p_dividend_payouts.investor_id`에 없는 값이라, 인덱스가 "없음"을 즉시 확인하고 끝나는 **가장 가벼운** 조회가 된다 — 혼잡 조건을 의도와 반대로 가볍게 만든다. 대신 시딩 스크립트(`docs/seed-settlement-timeout-assets.sql`)가 쓴 **결정론적 공식**(`md5('timeout-seed-investor-'||asset_no||'-'||i)::uuid`)으로 **그 라운드에 이미 트리거한 자산의 투자자 ID**를 직접 생성한다 — 그 자산은 라운드 r의 `quiet-r` 트리거로 이미 payout이 만들어진 상태라 실제 데이터에 맞는다. 라운드 r을 시작하기 전마다 이 스크립트로 `investors.csv`를 **그 라운드의 asset_no로 다시 만든다.**
   ```bash
   # <asset_no> = 이번 라운드(quiet-r)에서 쓴 1~3번 중 해당 번호
   { echo "userId";
     for i in $(seq 1 200); do
       raw=$(printf '%s' "timeout-seed-investor-<asset_no>-$i" | md5sum | awk '{print $1}')
       echo "${raw:0:8}-${raw:8:4}-${raw:12:4}-${raw:16:4}-${raw:20:12}"
     done; } > investors.csv
   ```
2. **Thread Group 만들기**: JMeter 실행 → **Test Plan** 우클릭 → **Add → Threads (Users) → Thread Group** → 이름을 `TG-load-dividends-me`로 변경. `Number of Threads (users)` = `30`(현재 `DB_POOL_MAX_SIZE=15`보다 커야 경쟁이 생긴다), `Ramp-up period` = `10`, **Loop Count 대신 하단 `Specify Thread lifetime`** 체크 → `Duration (seconds)` = `300`.
3. **CSV Data Set Config 추가**: `TG-load-dividends-me` 우클릭 → **Add → Config Element → CSV Data Set Config** → `Filename` = `investors.csv`, `Variable Names` = `userId`, `Ignore first line` = **True**, `Recycle on EOF` = **True**(스레드 수(30)가 CSV 행 수(200)보다 적으니 재사용), `Sharing mode` = **All threads**(스레드마다 다른 ID를 쓰게 분산).
4. **헤더 설정**: `TG-load-dividends-me` 우클릭 → **Add → Config Element → HTTP Header Manager** → **Add** 버튼으로 두 줄 추가: `X-User-Role` = `USER`, `X-User-Id` = `${userId}`(3번에서 만든 변수).
5. **HTTP Request 추가**: `TG-load-dividends-me` 우클릭 → **Add → Sampler → HTTP Request** → `Server Name or IP` = `<EC2 IP>`, `Port` = `19097`, `Method` = `GET`, `Path` = `/api/v1/dividends/me`.
   5-1. **Assertion 추가**: 그 HTTP Request 우클릭 → **Add → Assertions → Response Assertion** → Field to Test = Response Code, Pattern = `200`. 같은 Request에 **Add → Assertions → JSON Assertion** → `Assert JSON Path exists` = `$.success`, `Additionally assert value` 체크, Expected Value = `true`. 1라운드 초반에는 그 라운드 asset의 payout이 막 생긴 참이라 응답이 비정상이면 바로 드러나야 한다(응답 본문이 빈 배열이어도 `$.success=true`는 유지되므로 "빈 결과"와 "에러"를 구분해서 본다).
6. **결과 수집**: `TG-load-dividends-me` 우클릭 → **Add → Listener → Summary Report**(경량, 실측용). `View Results Tree`는 디버그 때만 켜고 실측 전에 반드시 **Disable**(우클릭 → Disable) — 응답 본문을 전부 메모리에 들고 있어서 느려진다.
7. **저장**: `File → Save Test Plan as...` → `scenario-timeout-load.jmx`.
8. **1회 디버그(GUI)**: 상단 녹색 ▶ **Start** 버튼으로 몇 초만 돌려 응답 코드 200·에러 0을 확인한 뒤 바로 ■ **Stop**.
9. **실측 실행(CLI, 비GUI)**:
   ```bash
   jmeter -n -t scenario-timeout-load.jmx -l results/timeout-load.jtl -Jhost=<EC2 IP>
   ```
   Duration(300초)이 끝나면 자동 종료된다. 중간에 멈추려면 터미널에서 `Ctrl+C`.
10. **혼잡이 실제로 걸렸다는 증거를 같이 남긴다** — "혼잡 구간이라 느려졌다"는 주장은 부하가 실제로 EC2에 걸렸다는 증거가 있어야 성립한다. JMeter 실행 중 `docker stats --no-stream`과 Summary Report의 처리량(req/s)을 캡처해둔다.

**7) timeout 값 확정** — 평온 3회 + 혼잡 3회(n=6)에서 나온 "저장"(flush(D−B) + 커밋(C−D)) 값을 모은다. ⚠️ **표준편차 기반 margin을 쓰지 않는다** — n=6처럼 표본이 적으면 표준편차 자체의 신뢰도가 낮고, "최댓값+2σ"는 최댓값·표준편차는 실측이어도 "2배"라는 배수는 측정값이 아니라 관례다. 대신 **최솟값·중앙값·최댓값(범위)로 보고한다.** 아래 "판정 기준"에서 미리 정한 규칙을 그대로 적용해 값을 정하고, 그 값을 **"관측 최댓값 X초에 안전마진을 둔 휴리스틱"**이라고 솔직하게 적는다(실측 기반 margin이라고 과대포장하지 않음). 결과를 `SettlementBatchWriter.java`의 주석에 반영하고, `docs/localTest.md`의 "정확한 값 미상"을 실측값(+ 위 휴리스틱 설명)으로 채운다.

### 판정 기준 (로컬 시계 실험 종료 후, EC2 측정 시작 전에 확정·커밋)

**커밋 시점을 여기로 못박는다.** timeout과 비교할 값이 A→C인지 B→C인지는 위 "시계 질문" 로컬 실험이 끝나야 알 수 있으므로, "측정 시작 전"이 아니라 **로컬 실험이 끝난 직후, EC2 Phase 1 1라운드 트리거 전**에 아래 기준을 숫자까지 확정해 커밋한다(예시 상태로 남겨두지 않는다 — 이 절 자체를 그 시점에 다시 다듬어 커밋해야 사전 등록이 된다):

- **기준 지표**: **B→C로 확정** — 로컬 시계 실험(위 "로컬 시계 실험 — 결과" 참고, 2026-10-05 실측) 결과 시계가 "대기"를 포함하지 않는 것으로 나왔다.
- **구간별 조치** (기준값 X의 평온+혼잡 전체 관측 최댓값 기준 — 상향 폭과 후속 조치를 구간별로 다르게 한다):
    - **X < 10초**: 20초 유지(여유 2배 이상 확보).
    - **10초 ≤ X < 20초**: `X + 10초`로 상향(지금 20초보다는 여유를 더 둔다는 뜻). 후속 조치는 없음 — 다음 재측정 주기에 다시 본다.
    - **X ≥ 20초**(지금 설정값과 같거나 넘음 — 실제 운영에서 이미 간당간당했을 수 있다는 뜻): `X + 10초`로 상향하고, **`reWriteBatchedInserts` 등 flush 비용 자체를 줄이는 작업을 후속 과제가 아니라 즉시 착수 대상으로 승격**한다.
    - **n=6 중 timeout 실패(롤백)가 1건이라도 있으면, 다른 트리거의 최댓값과 무관하게 무조건 `X ≥ 20초` 구간을 적용한다** — timeout에 걸려 롤백됐다는 사실 자체가 "실제 저장 시간이 최소 20초 이상이었다"는 직접 증거이기 때문이다(아래 실패 처리 규칙 4번과 연결).
- **왜 혼잡 라운드에서 실패가 나올 수 있는가**: 혼잡 라운드는 풀(15)에 JMeter 30 스레드의 `GET`이 걸린 상태에서 `POST /settlements`의 `persist()`를 트리거한다. 로컬 실험 결과가 "시계가 대기를 포함한다"(A→C)로 나오면, 이 혼잡 조건에서 **대기가 길어지는 것만으로도 timeout 실패가 현실적으로 자주 나올 수 있다** — 이건 이상 상황이 아니라 혼잡 조건을 거는 목적 자체가 드러나는 것이다.
- **실패 처리 규칙**: 혼잡 라운드에서 트리거가 실제로 timeout에 걸려 롤백될 수 있다. 이 경우:
    1. 그 라운드를 "실패"로 **기록하되 버리지 않는다** — 실패 횟수 자체가 "20초가 혼잡 상황에서 얼마나 아슬아슬한가"를 보여주는 결과다.
    2. `REQUIRES_NEW` 트랜잭션이라 타임아웃 시 해당 트랜잭션만 롤백된다 — 아래 쿼리로 그 배치의 행이 실제로 안 남아있는지 확인한다.
       ```bash
       docker compose --env-file .env -f infrastructure/docker-compose.yml exec -T postgres \
         psql -U moneytown -d settlement_db -c \
         "SELECT count(*) FROM p_settlement_batches WHERE asset_id = '<해당 asset_id>';"
       ```
       0이면 롤백 확인, 0이 아니면(부분 커밋 등 예상 밖 상태) 그 자체를 별도 이슈로 기록한다.
    3. 롤백이 확인되면 같은 asset/revenue는 재사용하지 않고 여유 자산으로 다음 라운드를 계속한다.
    4. **실패한 트리거의 값은 최댓값 계산에서 제외한다** — timeout 직전에 끊긴 값이라 실제 "저장"보다 짧게 기록돼 있어서, 그대로 최댓값 계산에 넣으면 판정이 낙관 쪽으로 틀어진다. 대신 **실패 횟수를 별도로 보고**하고, 그 라운드의 "저장"은 **최소 timeout 값 이상이었을 것으로 보수적으로 간주**한다.
    5. **혼잡 3회 중 2회 이상 실패하면, 나머지를 억지로 채우지 않고 그 자체(실패율)를 결과로 정리한다** — 이미 20초가 혼잡 조건에서 구조적으로 못 버틴다는 걸 보여주는 결과이므로 3라운드를 다 채우는 것보다 그 사실 자체가 더 중요한 결론이다.

이 기준이 있어야 "20초 유지"라는 결론이 나와도 "그냥 안 바꿨네"가 아니라 "미리 정한 기준을 충족해서 유지했다"고 설명할 수 있다.

### 타임아웃 값 결정에 넣을 논거

- **비대칭 비용** — timeout이 너무 짧으면 정상 저장이 롤백된다. 이미 끝난 holdings 페이징(이번엔 A0→A로 직접 측정, 추정값 아님)이 통째로 낭비되고 운영자가 회차 개시를 다시 눌러야 한다. 반대로 너무 길면 커넥션 점유가 조금 길어질 뿐이다 — 이 비대칭이 "짧게 잡아 실패 비용을 줄이기"보다 "넉넉하게 잡아 낭비를 막기" 쪽으로 margin을 둘 근거가 된다.
- **시딩한 최대 규모에서 측정** — 코드에 "회차당 payout 상한 1만 건" 같은 제약은 없다(`AssetHoldingsSnapshotFetcher`의 `MAX_PAGES=1000`은 페이지네이션 안전장치일 뿐 투자자 수 상한이 아님, 코드로 확인). 그래서 "1만 건이 곧 최악 케이스"라고 쓰지 않는다 — 대신 **"이번 실험에서 시딩한 최대 규모(자산당 투자자 1만 명)에서 측정했다"**고 쓰고, 실제 운영 규모가 이보다 커질 수 있다는 전제를 결과에 남긴다.
- **변수 통제(별도 과제 분리)** — `reWriteBatchedInserts` 미설정은 저장 시간을 줄일 수 있는 후속 개선 후보지만, 이번 측정에는 **섞지 않는다**. 지금 같이 고치면 "timeout 값이 변했다"가 "reWriteBatchedInserts 때문"인지 "원래 분석이 틀려서"인지 구분이 안 된다. 타임아웃 근거를 먼저 확정하고, 그 다음에 별도로 `reWriteBatchedInserts`를 켜서 "저장"이 얼마나 줄어드는지(그리고 timeout을 다시 낮출 수 있는지) 측정하는 걸 후속 과제로 남긴다.

### 숫자 쓰는 규칙 (STAR 작성 시 — "추정"이라는 단어를 쓰지 않기 위해)

모든 숫자를 아래 3층 중 하나로 분류하고, 어느 층인지 출처와 함께 적는다. "약", "~로 보인다", "추정"은 STAR 초안에 한 번도 나오지 않아야 한다 — 다 쓰고 나서 이 세 단어로 검색해 확인한다.

1. **측정** — 로그(A0/A/B/D/C 원본 타임스탬프), `pg_stat_statements`, actuator 지표에서 직접 나온 값. 원본 로그 파일과 `(quiet|congested)-r` 순번을 함께 보관해 "이 값이 몇 번째 실행의 것인지" 언제든 추적 가능해야 한다.
2. **코드 확인** — 파일과 라인을 인용한다(예: "`hibernate.jdbc.batch_size: 100` — `config-repository/settlement-service.yml:28`", "`DB_POOL_MAX_SIZE: 15` — `infrastructure/docker-compose.yml:351`").
3. **설계 결정** — 판정 임계값(10초/20초)과 상향 폭(+10초), 시딩 규모(투자자 1만 명)는 측정값이 아니라 **우리가 정한 값**이다. "설계상의 선택"이라고 명시하고 그렇게 정한 이유를 붙인다(시나리오 A에서 "풀 크기 15는 개념증명을 위해 일부러 작게 잡은 값"이라고 밝힌 것과 같은 방식).

최종 timeout 값은 **"측정 n=6(원본 값 전부 보관)과 사전에 커밋한 판정 규칙을 그대로 적용해 정한 값"**이라고 쓴다 — 이러면 추정이 아니라 측정+사전 규칙의 결과라는 게 분명해진다.

### 사전 등록 증거 (로컬 실험 종료 후, EC2 1라운드 전에 커밋)

"판정 기준" 절의 수치와 근거를 **로컬 시계 실험이 끝난 직후, EC2 Phase 1의 1라운드를 트리거하기 전에** git에 커밋해둔다(`docs/portfolio.md` 이 절 자체를 커밋해도 된다) — "측정 시작 전"이 아니라 이 시점인 이유는 위 "판정 기준" 절에 적은 그대로, 기준 지표(A→C인지 B→C인지)가 로컬 실험 결과로만 정해지기 때문이다. **커밋 메시지에 그 결과로 확정한 기준 지표(A→C 또는 B→C)를 적어두면 "왜 이 시점에 커밋했는지" 근거가 커밋 하나로 완결된다.** STAR의 Result에 **"측정 전 커밋 `<해시>`에서 정한 기준을 그대로 적용했다"**고 쓸 수 있게 되고, 이게 "결과 보고 끼워 맞췄다"는 반박을 막는 가장 강한 근거다.

### 기록해둘 것 (STAR 재료)

- **측정 환경표**: 인스턴스 타입, 커밋 해시, `DB_POOL_MAX_SIZE`, `hibernate.jdbc.batch_size`, 트레이싱 샘플링(`TRACING_SAMPLING_PROBABILITY`) 값 — EC2(n=6)와 로컬(시계 실험)을 **환경이 다른 별도 행**으로 표에 분리하고, "로컬 값은 시계 판정에만 쓰고 timeout 수치 산출에는 쓰지 않았다"를 한 줄로 명시.
- **회차별 A0/A/B/D/C 타임스탬프 원본 + `pg_stat_statements` 결과** — 평온/혼잡 구분, `(quiet|congested)-r` 실행 순서 포함.
- **로컬 시계 실험 결과** — 풀=2(처음 시도한 풀=1은 Flyway 자기 데드락으로 기동 실패해 2로 조정)·timeout=3·자산 3개(1개 큼+2개 경쟁) 조건에서 실측한 대기/저장 값, 예외가 대기/flush/커밋 중 어디서 났는지.
- **혼잡이 실제로 걸렸다는 증거** — CPU/처리량 캡처.
- **판정 기준을 커밋한 해시** — 위 "사전 등록 증거".
- **실패한 시도와 정정 내역** — "측정 버그 → 원인 → 정정" 과정 자체가 이 항목의 핵심 서사다(배경 절의 "14.2~16.7초 철회"가 그 시작이고, 로컬 시계 실험도 같은 성격의 자기검증이다).

### STAR 구성 방향 (측정 후 작성)

- **Situation**: 20초의 근거(14.2~16.7초)가 로그 타이밍 버그로 무효화됐고, 그 후 시도한 교차검증도 독립적이지 못했다(같은 로그 체계에 의존하거나, 다른 측정값에서 역산한 값이었다).
- **Task**: 독립된 두 지표(앱 로그 + `pg_stat_statements`)로 "저장"을 flush·커밋으로 쪼개 다시 재고, 그 전에 먼저 "20초 타임아웃이 정확히 무엇을 재는 구간인지"부터 로컬에서 실제로 터뜨려 확인한다. 평온·혼잡 두 조건에서 판정 기준을 측정 전에 커밋해두고 그 기준으로 타임아웃을 정한다.
- **Result**: 사전에 커밋한 판정 기준(해시 인용) 대비 실측 결과와 최종 timeout 값. 평온과 혼잡의 차이는 숫자로 제시하되, 표본 수와 그로 인한 한계를 같이 적는다.
- **Learn**: 측정 도구 자체도 검증 대상이다 — 같은 로그 하나로 스스로를 검증하지 말고, 반드시 독립된 지표로 교차검증해야 "측정 버그"를 또 반복하지 않는다. 숫자는 측정/코드확인/설계결정 세 층으로 구분해서 쓴다.

**분량 — STAR 본문에 이 설계 문서를 그대로 옮기지 않는다.** 그러면 인덱스 항목(시나리오 0)의 절반도 안 되게 압축해야 한다. 로컬 시계 실험과 EC2 6회 라운드를 다 돌려도 STAR엔 "시계가 무엇을 재는지 로컬에서 확인, 기준을 사전 커밋, 결과 X초로 Y 판정" 세 문장 정도만 들어간다. STAR 본문은 이 세 가지(시계 결론, 판정 기준 커밋 해시, 최종 숫자)에만 집중하고, 나머지 절차 디테일은 이 설계 문서를 부록으로 가리킨다.

- **결과가 "20초 유지"로 나올 때**: Result에 "관측 최댓값 X초 < 사전 기준 10초라 유지"라고 쓰고, Learn에 "값은 그대로여도 근거가 측정 버그가 있던 값에서 실측값으로 바뀌었다"를 넣는다 — 값이 안 바뀐 게 약점이 아니라 서사의 일부가 된다.
- **결과가 "상향"으로 나올 때**: 기존 20초가 혼잡 상황에서 실제로 위험했다는 발견이 되므로, 이 경우 서사가 더 강해진다.

### 실행 직전 마지막 점검 체크리스트

- [ ] 코드의 로그 메시지(A0/A/B/D/C)와 4)·6번·로컬 실험의 grep 패턴이 글자 단위로 일치하는가.
- [ ] 시딩 스크립트(`docs/seed-settlement-timeout-assets.sql`)의 투자자 UUID 공식과 JMeter CSV 생성 공식이 같은가 — 투자자 1명으로 `GET /dividends/me`를 직접 호출해 빈 결과가 아닌지 확인.
- [ ] 명시적 `flush()`가 들어간 빌드(D 로그 포함)로 쟀다는 사실을 측정 환경표에 기록했는가.

### 현재 상태

설계를 리뷰 5회(실행 절차 허점 → 구간 정의/통계 해석 허점 → EC2 Phase 0의 논리·순서·데이터 허점 → 문서 내부 수치 불일치·실패 처리 공백 → 실험 규모의 과잉)를 거쳐 다음 상태로 수렴했다:
- 진단 로그를 A/B/C 3개에서 **A0/A/B/D/C 5개로 확장**(코드 반영 완료: `SettlementCommandService.java`, `SettlementBatchWriter.java`) — 홀딩스 페이징을 직접 측정하고, "저장"을 flush/커밋으로 쪼갰다.
- "시계가 대기를 포함하는가"는 **EC2 Phase 0(풀 축소+동시 트리거, 자산 25개)가 아니라 로컬 미니 실험(풀=2, timeout=3, 자산 3개)으로 대체**했다 — 같은 결론을 훨씬 적은 비용으로, EC2 재배포 없이 얻는다. 처음엔 풀=1·자산 2개로 설계했다가, 실제로 로컬에 띄워보니 **풀=1에서 Flyway가 기동 시점에 자기 자신과 데드락**한다는 게 실측으로 드러나(소스 추론으로는 안 나왔을 문제) 풀=2·자산 3개(큰 것 1 + 경쟁용 2)로 조정했다 — 이 과정 자체가 "실제로 돌려봐야 검증이 된다"는 이 항목의 핵심 주장을 한 번 더 증명한 셈이다.
- `/actuator/metrics/*`(settlement-service 전용으로만 노출 확대)·`DB_POOL_MAX_SIZE`(compose 하드코딩 제거) 등 실행 전에 걸렸을 코드·설정 버그를 코드로 직접 확인해 고쳤다.
- **평온+혼잡을 5+5에서 3+3(n=6)으로 줄였다** — 혼잡 조건에서의 분포가 이 항목의 핵심 가치이므로 n=1~2까지는 줄이지 않았고, 대신 EC2 쪽에서 비용이 큰 CPU 크레딧 기록·스케줄러 소음 표시를 "상시 수행"에서 "값이 이상할 때만 확인하는 보조 절차"로 낮췄다. 시딩도 25개에서 10개로 줄었다.
- 판정 기준의 커밋 시점을 "로컬 실험 종료 후"로 명확히 하고, 실패 처리 규칙·숫자 3계층 구분 규칙·STAR 분량 가이드를 그대로 유지했다.

**로컬 시계 실험을 2026-10-05 실제로 실행해 완료했다** — 자산 2가 HikariCP `connection-timeout`과 정확히 일치하는 30.003초를 대기한 뒤에도 `timeout=3`에 걸리지 않고 성공해, **기준 지표가 B→C로 확정**됐다(위 "로컬 시계 실험 — 결과" 참고). 로컬 코드(`timeout=20`)·`.env`(`DB_POOL_MAX_SIZE`)는 원복하고 재배포해 `{"status":"UP"}`으로 확인했다.

다음 단계는 "판정 기준" 절을 이 확정된 기준(B→C)으로 다시 다듬어 커밋해 사전 등록 증거를 남긴 뒤, EC2 Phase 1(1~7)을 순서대로 실행하는 것이다. 측정 결과가 나오면 이 절을 위 STAR 방향대로, "추정" 표현 없이 완결된 항목으로 다시 정리한다.

---
