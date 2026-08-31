# Asset Service API 정합성 검토 및 추가 명세

## 검토 결과 요약

노션의 SA 문서, 2차 설계 정합성 검사, 공모·청약 ↔ Asset/Holding 협의 문서를 기존 Asset API 19개와 대조했다.

### 반드시 신규 추가할 API

| 구분 | API 이름 | Method | path (`/api/v1` prefix 생략) | 권한 | 우선순위 | 추가 이유 |
| --- | --- | --- | --- | --- | --- | --- |
| 신규 | 수익 데이터 등록·표준화 | POST | `/assets/{assetId}/revenues` | ISSUER, ADMIN, SYSTEM | MVP | 서비스 범위에는 수익 등록·표준화가 있지만 등록 API가 없음 |
| 신규 | 자산별 수익 목록 조회 | GET | `/assets/{assetId}/revenues` | ISSUER, ADMIN | MVP | 등록 결과와 정산 전달 상태를 운영자가 확인할 API가 없음 |
| 신규 | 정산 전달 대기 수익 목록 조회 | GET | `/internal/revenues` | SYSTEM | MVP | Settlement 스케줄러가 `READY` 수익을 가져갈 API가 없음 |
| 신규 | 자산 대표 이미지 등록·교체 | PUT | `/assets/{assetId}/representative-image` | ISSUER, ADMIN | MVP | 대표 이미지 S3 업로드 요구사항은 있으나 대응 API가 없음 |
| 신규 | 자산 종료·최종 정산 요청 | POST | `/assets/{assetId}/termination-requests` | ISSUER, ADMIN | 도전 | 자산 종료 후 원금 반환 흐름을 시작할 진입 API가 없음 |

### 반드시 수정할 기존 API

| 구분 | 기존 API | 변경 후 | 변경 이유 |
| --- | --- | --- | --- |
| 수정 | `PATCH /assets/{assetId}/status` | 경로 유지, 역할별 상태 전이 제한 추가 | ISSUER가 자신이 등록한 자산을 직접 승인할 수 있는 권한 허점 방지 |
| 수정 | `POST /internal/v1/holdings/allocations` | `POST /internal/holdings/allocations` | API 표에 `/api/v1` prefix를 생략하므로 `internal/v1`을 쓰면 버전이 중복됨 |
| 교체 | `POST /internal/v1/holdings/{holdingId}/revocations` | `POST /internal/holdings/revocations` | 공모 보상 계약은 `holdingId`가 아니라 `subscriptionId` 기준 멱등 회수 및 `NO_ACTION` 처리가 필요함 |

### 추가하지 않아도 되는 API

| 후보 | 판단 | 이유 |
| --- | --- | --- |
| 공모 생성용 자산 Snapshot 전용 조회 API | 추가하지 않음 | 기존 `GET /assets/{assetId}` 응답에 가격·총발행량·상태·버전을 포함하면 Offering이 조회 후 자체 Snapshot으로 저장 가능 |
| 청약별 Holding 상태 조회 API | MVP에는 추가하지 않음 | 정상 흐름은 Kafka 결과 이벤트와 Outbox 재발행으로 처리. 운영 대사 기능이 필요해질 때 추가 |
| Synthetic Data 생성 API | 추가하지 않음 | 운영 API보다 테스트 Fixture 또는 관리자 배치가 안전하고 단순함 |
| Holding의 `REVOKE_REQUESTED` 변경 API | 추가하지 않음 | 회수는 Asset Service 내부 트랜잭션으로 처리하고 결과만 반환. 외부에서 중간 상태를 직접 조작하지 않음 |

---

# 신규 API 1. 수익 데이터 등록·표준화

**Method:** `POST`  
**Full Path:** `/api/v1/assets/{assetId}/revenues`  
**노션 path 입력값:** `/assets/{assetId}/revenues`  
**권한:** `ISSUER`, `ADMIN`, `SYSTEM`

### 1. 목적 및 요약

부동산 임대관리 시스템, 음원 저작권 신탁 시스템 또는 관리자가 전달한 서로 다른 수익 데이터를 Asset Service의 공통 Revenue 형식으로 변환해 저장한다.

Asset Service는 외부 데이터의 **필드명과 포맷만 표준화**한다. 비용 공제 가능 여부, 정산 기간 포함 여부 및 최종 배당 가능 금액 계산은 Settlement Service가 담당한다.

### 2. 제약사항 및 비즈니스 로직

| 구분 | 항목 | 내용 |
| --- | --- | --- |
| 제약 | 대상 자산 | 존재하고 `deletedAt = null`이며 `APPROVED` 또는 `SUSPENDED` 상태인 자산만 등록 가능 |
| 권한 | ISSUER | 자신이 운용하는 자산의 수익만 등록 가능 |
| 권한 | ADMIN | 모든 자산의 수익 등록 가능 |
| 권한 | SYSTEM | 인증된 외부 연동 또는 내부 배치만 호출 가능 |
| 멱등성 | 원본 중복 방지 | `(assetId, sourceType, sourceReferenceId)` 조합을 UNIQUE로 관리 |
| 검증 | 수익 유형 | 부동산은 `RENTAL_INCOME`, 음원은 `COPYRIGHT_ROYALTY`를 기본 허용 |
| 검증 | 금액 | `grossAmount > 0`, `expenseAmount >= 0`, `feeAmount >= 0` |
| 검증 | 기간 | `periodStart <= periodEnd` |
| 검증 | 통화 | MVP에서는 `KRW`만 허용 |
| 상태 | 최초 상태 | 저장 시 `transferStatus = READY` |
| 책임 분리 | 배당 가능액 | Asset Service에서 `distributableAmount`를 계산하지 않음 |

### 3. 입출력 명세

- Request Header 예시

  ```http
  Authorization: Bearer {accessToken}
  Idempotency-Key: revenue-property-manager-202608-0001
  Content-Type: application/json
  ```

- Request Body 예시

  ```json
  {
    "sourceType": "PROPERTY_MANAGER",
    "sourceReferenceId": "PM-RENT-202608-0001",
    "revenueType": "RENTAL_INCOME",
    "grossAmount": 15000000.00,
    "expenseAmount": 1200000.00,
    "feeAmount": 300000.00,
    "currency": "KRW",
    "periodStart": "2026-08-01",
    "periodEnd": "2026-08-31",
    "rawPayload": {
      "buildingCode": "BLD-GN-001",
      "vacancyRate": 0.03
    }
  }
  ```

- Response 예시

  ```json
  {
    "revenueId": "018f22d3-4c4a-7a39-b30f-11060fe30c77",
    "assetId": "018f1c10-2f49-7f72-8d42-8d56ac18f7ad",
    "sourceType": "PROPERTY_MANAGER",
    "sourceReferenceId": "PM-RENT-202608-0001",
    "revenueType": "RENTAL_INCOME",
    "grossAmount": 15000000.00,
    "expenseAmount": 1200000.00,
    "feeAmount": 300000.00,
    "currency": "KRW",
    "periodStart": "2026-08-01",
    "periodEnd": "2026-08-31",
    "transferStatus": "READY",
    "createdAt": "2026-08-30T12:00:00Z"
  }
  ```

### 4. 응답 코드

| HTTP Status | code (Custom Code) | message |
| --- | --- | --- |
| 201 |  | 수익 데이터가 등록되었습니다. |
| 400 | R400 | 수익 데이터 형식 또는 금액이 올바르지 않습니다. |
| 401 | G001 | 인증되지 않은 요청입니다. |
| 403 | G002 | 해당 자산의 수익을 등록할 권한이 없습니다. |
| 404 | A404 | 존재하지 않는 자산입니다. |
| 409 | R409 | 이미 등록된 원본 수익 데이터입니다. |

---

# 신규 API 2. 자산별 수익 목록 조회

**Method:** `GET`  
**Full Path:** `/api/v1/assets/{assetId}/revenues`  
**노션 path 입력값:** `/assets/{assetId}/revenues`  
**권한:** `ISSUER`, `ADMIN`

### 1. 목적 및 요약

자산운용자 또는 관리자가 특정 자산에 등록된 수익과 Settlement Service 전달 상태를 조회한다.

### 2. 제약사항 및 비즈니스 로직

| 구분 | 항목 | 내용 |
| --- | --- | --- |
| 권한 | ISSUER | 자신이 운용하는 자산만 조회 가능 |
| 권한 | ADMIN | 모든 자산 조회 가능 |
| 필터 | 전달 상태 | `READY`, `TRANSFERRED`, `FAILED` |
| 필터 | 기간 | `periodStart`, `periodEnd`로 수익 발생 기간 검색 |
| 페이징 | 방식 | 대용량 조회를 고려해 Cursor Pagination 사용 |
| 정렬 | 기본값 | `createdAt DESC, revenueId DESC` |
| 제외 | 삭제 자산 | `deletedAt`이 존재하는 자산은 조회 불가 |

### 3. 입출력 명세

- Request 예시

  ```http
  GET /api/v1/assets/018f1c10-2f49-7f72-8d42-8d56ac18f7ad/revenues?transferStatus=READY&periodStart=2026-08-01&periodEnd=2026-08-31&size=20
  Authorization: Bearer {accessToken}
  ```

- Response 예시

  ```json
  {
    "items": [
      {
        "revenueId": "018f22d3-4c4a-7a39-b30f-11060fe30c77",
        "assetId": "018f1c10-2f49-7f72-8d42-8d56ac18f7ad",
        "sourceType": "PROPERTY_MANAGER",
        "revenueType": "RENTAL_INCOME",
        "grossAmount": 15000000.00,
        "expenseAmount": 1200000.00,
        "feeAmount": 300000.00,
        "currency": "KRW",
        "periodStart": "2026-08-01",
        "periodEnd": "2026-08-31",
        "transferStatus": "READY",
        "failureReason": null,
        "createdAt": "2026-08-30T12:00:00Z"
      }
    ],
    "nextCursor": "MjAyNi0wOC0zMFQxMjowMDowMFpfMDE4ZjIyZDM=",
    "hasNext": false
  }
  ```

### 4. 응답 코드

| HTTP Status | code (Custom Code) | message |
| --- | --- | --- |
| 200 |  | 수익 목록 조회에 성공했습니다. |
| 400 | R400 | 조회 조건이 올바르지 않습니다. |
| 401 | G001 | 인증되지 않은 요청입니다. |
| 403 | G002 | 해당 자산의 수익을 조회할 권한이 없습니다. |
| 404 | A404 | 존재하지 않는 자산입니다. |

---

# 신규 API 3. 정산 전달 대기 수익 목록 조회

**Method:** `GET`  
**Full Path:** `/api/v1/internal/revenues`  
**노션 path 입력값:** `/internal/revenues`  
**권한:** `SYSTEM`

### 1. 목적 및 요약

MVP 기간에 Settlement Service의 스케줄러가 Asset Service에서 정산 전달을 기다리는 수익 데이터를 조회한다. 도전 기간에 Kafka 기반 전달로 변경하더라도 장애 복구와 대사 용도로 유지할 수 있다.

### 2. 제약사항 및 비즈니스 로직

| 구분 | 항목 | 내용 |
| --- | --- | --- |
| 권한 | 호출 주체 | 서비스 인증을 통과한 Settlement Service만 호출 가능 |
| 필수 필터 | 전달 상태 | MVP 기본값은 `READY`이며 `FAILED` 재처리 조회도 허용 |
| 페이징 | 방식 | Cursor Pagination, 기본 100건, 최대 500건 |
| 정렬 | 기본값 | `createdAt ASC, revenueId ASC`로 오래된 건부터 반환 |
| 상태 변경 | 조회 시점 | GET 호출만으로 `transferStatus`를 변경하지 않음 |
| 중복 대응 | 정산 서비스 | 동일 `revenueId`가 재조회돼도 Settlement Service가 멱등 처리 |
| 완료 처리 | 후속 호출 | 정산 서비스 적재 성공 후 기존 `PATCH /assets/revenues/{revenueId}/transfer-status`를 호출 |

### 3. 입출력 명세

- Request 예시

  ```http
  GET /api/v1/internal/revenues?transferStatus=READY&size=100
  X-Service-Name: settlement-service
  Authorization: Bearer {serviceToken}
  ```

- Response 예시

  ```json
  {
    "items": [
      {
        "revenueId": "018f22d3-4c4a-7a39-b30f-11060fe30c77",
        "assetId": "018f1c10-2f49-7f72-8d42-8d56ac18f7ad",
        "sourceType": "PROPERTY_MANAGER",
        "sourceReferenceId": "PM-RENT-202608-0001",
        "revenueType": "RENTAL_INCOME",
        "grossAmount": 15000000.00,
        "expenseAmount": 1200000.00,
        "feeAmount": 300000.00,
        "currency": "KRW",
        "periodStart": "2026-08-01",
        "periodEnd": "2026-08-31",
        "transferStatus": "READY",
        "createdAt": "2026-08-30T12:00:00Z"
      }
    ],
    "nextCursor": null,
    "hasNext": false
  }
  ```

### 4. 응답 코드

| HTTP Status | code (Custom Code) | message |
| --- | --- | --- |
| 200 |  | 정산 전달 대기 수익 목록 조회에 성공했습니다. |
| 400 | R400 | 조회 조건이 올바르지 않습니다. |
| 401 | G001 | 인증되지 않은 서비스 요청입니다. |
| 403 | G002 | Settlement Service만 호출할 수 있습니다. |

---

# 신규 API 4. 자산 대표 이미지 등록·교체

**Method:** `PUT`  
**Full Path:** `/api/v1/assets/{assetId}/representative-image`  
**노션 path 입력값:** `/assets/{assetId}/representative-image`  
**권한:** `ISSUER`, `ADMIN`  
**Content-Type:** `multipart/form-data`

### 1. 목적 및 요약

자산운용자 또는 관리자가 자산 목록과 상세 화면에 사용할 대표 이미지를 S3에 업로드하거나 기존 이미지를 교체한다.

### 2. 제약사항 및 비즈니스 로직

| 구분 | 항목 | 내용 |
| --- | --- | --- |
| 권한 | ISSUER | 자신이 운용하는 자산만 변경 가능 |
| 권한 | ADMIN | 모든 자산 변경 가능 |
| 파일 | 허용 형식 | `image/jpeg`, `image/png`, `image/webp` |
| 파일 | 최대 크기 | 10MB 이하 |
| 저장 | DB 값 | S3 전체 URL이 아닌 `representativeImageKey`만 저장 |
| 교체 | 처리 순서 | 신규 파일 업로드 성공 후 DB Object Key 변경 |
| 보상 | DB 변경 실패 | 새로 업로드한 미참조 S3 객체를 삭제 대상으로 등록 |
| 캐시 | 무효화 | 자산 상세·목록 Redis 캐시 삭제 |

### 3. 입출력 명세

- Request 예시

  ```http
  PUT /api/v1/assets/018f1c10-2f49-7f72-8d42-8d56ac18f7ad/representative-image
  Authorization: Bearer {accessToken}
  Content-Type: multipart/form-data

  file=@gangnam-office.webp
  ```

- Response 예시

  ```json
  {
    "assetId": "018f1c10-2f49-7f72-8d42-8d56ac18f7ad",
    "representativeImageKey": "assets/018f1c10/representative/20260830-01.webp",
    "contentType": "image/webp",
    "fileSize": 845312,
    "updatedAt": "2026-08-30T12:10:00Z"
  }
  ```

### 4. 응답 코드

| HTTP Status | code (Custom Code) | message |
| --- | --- | --- |
| 200 |  | 대표 이미지가 등록되었습니다. |
| 400 | D400 | 지원하지 않는 이미지 형식입니다. |
| 401 | G001 | 인증되지 않은 요청입니다. |
| 403 | G002 | 해당 자산의 이미지를 변경할 권한이 없습니다. |
| 404 | A404 | 존재하지 않는 자산입니다. |
| 413 | D413 | 이미지 파일 크기가 제한을 초과했습니다. |
| 500 | D500 | 이미지 저장 중 오류가 발생했습니다. |

---

# 신규 API 5. 자산 종료·최종 정산 요청 — 도전 기간

**Method:** `POST`  
**Full Path:** `/api/v1/assets/{assetId}/termination-requests`  
**노션 path 입력값:** `/assets/{assetId}/termination-requests`  
**권한:** `ISSUER`, `ADMIN`  
**개발우선순위:** 도전 기능

### 1. 목적 및 요약

자산운용자 또는 관리자가 자산 운영 종료와 투자자 원금 반환을 위한 최종 정산을 요청한다. 요청 즉시 자산을 `TERMINATED`로 바꾸지 않고 최종 정산이 완료된 뒤 종료 상태로 전환한다.

### 2. 제약사항 및 비즈니스 로직

| 구분 | 항목 | 내용 |
| --- | --- | --- |
| 대상 상태 | 허용 상태 | `APPROVED` 또는 `SUSPENDED` 자산만 요청 가능 |
| 권한 | ISSUER | 자신이 운용하는 자산만 요청 가능 |
| 중복 방지 | 멱등성 | `Idempotency-Key`와 자산별 진행 중 요청 1건 제한 |
| 사전 검증 | 공모 | `SCHEDULED`, `OPEN`, `CANCELLING`, `COMPENSATING` 상태 공모가 없어야 함 |
| 정산 기준 | 원금 | MVP 합의에 따라 `unitPrice × 보유수량` 기준으로 원금 반환 |
| 상태 | 요청 직후 | 최종 정산 요청 이벤트를 발행하고 HTTP 202 반환 |
| 상태 | 완료 후 | `FinalSettlementCompleted` 수신 후 자산을 `TERMINATED`로 전환 |
| Holding | 종료 처리 | 최종 정산 완료 Record Date 이후의 Holding을 종료 처리 |

### 3. 입출력 명세

- Request Header 예시

  ```http
  Authorization: Bearer {accessToken}
  Idempotency-Key: terminate-asset-018f1c10-20260830
  Content-Type: application/json
  ```

- Request Body 예시

  ```json
  {
    "recordDate": "2026-09-30",
    "reason": "부동산 운용 계약 종료에 따른 원금 반환"
  }
  ```

- Response 예시

  ```json
  {
    "terminationRequestId": "018f33ab-934e-7a15-8404-b957e216cb4f",
    "assetId": "018f1c10-2f49-7f72-8d42-8d56ac18f7ad",
    "recordDate": "2026-09-30",
    "status": "REQUESTED",
    "requestedAt": "2026-08-30T12:30:00Z"
  }
  ```

### 4. 응답 코드

| HTTP Status | code (Custom Code) | message |
| --- | --- | --- |
| 202 |  | 자산 종료 및 최종 정산 요청이 접수되었습니다. |
| 400 | A400 | 종료 요청 정보가 올바르지 않습니다. |
| 401 | G001 | 인증되지 않은 요청입니다. |
| 403 | G002 | 해당 자산의 종료를 요청할 권한이 없습니다. |
| 404 | A404 | 존재하지 않는 자산입니다. |
| 409 | A409 | 진행 중인 공모 또는 최종 정산 요청이 존재합니다. |

---

# 수정 API 1. 자산 상태 변경

**Method:** `PATCH`  
**Full Path:** `/api/v1/assets/{assetId}/status`  
**노션 path 입력값:** `/assets/{assetId}/status`  
**권한:** `ISSUER`, `ADMIN`

### 1. 목적 및 요약

자산의 심사·운영 생애주기 상태를 변경한다. 기존 API 경로는 유지하되 호출자 역할에 따라 허용되는 상태 전이를 서버에서 강제한다.

### 2. 제약사항 및 비즈니스 로직

| 호출자 | 현재 상태 | 변경 가능 상태 | 추가 조건 |
| --- | --- | --- | --- |
| ISSUER | `DRAFT`, `REJECTED` | `REVIEW_REQUESTED` | 자신이 운용하는 자산만 가능 |
| ADMIN | `REVIEW_REQUESTED` | `APPROVED` | 심사 완료 자산만 가능 |
| ADMIN | `REVIEW_REQUESTED` | `REJECTED` | `rejectionReason` 필수 |
| ADMIN | `APPROVED` | `SUSPENDED` | 중단 사유 필수 |
| ADMIN | `SUSPENDED` | `APPROVED` | 재개 사유 기록 |
| SYSTEM | 최종 정산 완료 대상 | `TERMINATED` | `FinalSettlementCompleted` 처리 시에만 가능 |

`OFFERING_READY`, `OFFERING`, `SOLD_OUT`, `CLOSED`, `CANCELLED`는 Offering Service의 상태이므로 Asset 상태에 포함하지 않는다. ISSUER와 ADMIN이 요청 Body에 임의 상태를 넣더라도 위 전이표에 없는 변경은 거부한다.

### 3. 입출력 명세

- Request 예시 — 자산운용자 심사 요청

  ```json
  {
    "status": "REVIEW_REQUESTED"
  }
  ```

- Request 예시 — 관리자 반려

  ```json
  {
    "status": "REJECTED",
    "reason": "권리 증빙 문서의 유효기간이 만료되었습니다."
  }
  ```

- Response 예시

  ```json
  {
    "assetId": "018f1c10-2f49-7f72-8d42-8d56ac18f7ad",
    "previousStatus": "REVIEW_REQUESTED",
    "assetStatus": "REJECTED",
    "rejectionReason": "권리 증빙 문서의 유효기간이 만료되었습니다.",
    "updatedAt": "2026-08-30T13:00:00Z"
  }
  ```

### 4. 응답 코드

| HTTP Status | code (Custom Code) | message |
| --- | --- | --- |
| 200 |  | 자산 상태가 변경되었습니다. |
| 400 | A400 | 변경 상태 또는 필수 사유가 올바르지 않습니다. |
| 401 | G001 | 인증되지 않은 요청입니다. |
| 403 | G002 | 해당 상태로 변경할 권한이 없습니다. |
| 404 | A404 | 존재하지 않는 자산입니다. |
| 409 | A409 | 현재 상태에서는 요청한 상태로 변경할 수 없습니다. |

---

# 수정 API 2. 청약 확정 지분 배정

**Method:** `POST`  
**Full Path:** `/api/v1/internal/holdings/allocations`  
**노션 path 입력값:** `/internal/holdings/allocations`  
**권한:** `SYSTEM`

### 1. 목적 및 요약

Offering Service에서 확정된 청약을 기준으로 투자자의 보유지분을 생성하거나 증가시킨다. 같은 청약 확정 요청이 여러 번 전달돼도 지분은 한 번만 배정한다.

### 2. 제약사항 및 비즈니스 로직

| 구분 | 항목 | 내용 |
| --- | --- | --- |
| 권한 | 호출 주체 | 인증된 Offering Service 또는 Kafka Consumer 내부 처리만 허용 |
| 멱등성 | 기준 | `subscriptionId + ALLOCATE` 조합은 한 번만 처리 |
| 자산 | 상태 | `TERMINATED` 자산에는 지분 배정 불가 |
| 수량 | 검증 | `quantity > 0`이고 배정 후 `allocatedQuantity <= totalShareQuantity` |
| 트랜잭션 | 처리 범위 | `p_holdings`, `p_holding_histories`, `p_assets.allocated_quantity`를 하나의 DB 트랜잭션으로 갱신 |
| 보유지분 | 집계 기준 | 동일 `(assetId, userId)` Holding이 있으면 수량 증가, 없으면 생성 |
| 중복 요청 | 응답 | 오류 대신 기존 처리 결과와 `ALREADY_PROCESSED` 반환 |

### 3. 입출력 명세

- Request Header 예시

  ```http
  Authorization: Bearer {serviceToken}
  Idempotency-Key: allocate-subscription-018f44aa
  ```

- Request Body 예시

  ```json
  {
    "subscriptionId": "018f44aa-caf4-7d5d-83ec-b926f3d78c90",
    "offeringId": "018f4191-1c2b-7e44-a610-79215060c421",
    "assetId": "018f1c10-2f49-7f72-8d42-8d56ac18f7ad",
    "userId": "d1a2b3c4-8bd6-4810-b95d-8f0c3fc92622",
    "quantity": 10
  }
  ```

- Response 예시

  ```json
  {
    "result": "ALLOCATED",
    "holdingId": "018f45a0-8419-75e5-9218-e99e494391ec",
    "subscriptionId": "018f44aa-caf4-7d5d-83ec-b926f3d78c90",
    "assetId": "018f1c10-2f49-7f72-8d42-8d56ac18f7ad",
    "userId": "d1a2b3c4-8bd6-4810-b95d-8f0c3fc92622",
    "allocatedQuantity": 10,
    "balanceBefore": 0,
    "balanceAfter": 10,
    "processedAt": "2026-08-30T13:20:00Z"
  }
  ```

중복 요청이면 `result`만 `ALREADY_PROCESSED`로 반환하고 수량은 다시 증가시키지 않는다.

### 4. 응답 코드

| HTTP Status | code (Custom Code) | message |
| --- | --- | --- |
| 200 |  | 지분 배정 처리에 성공했습니다. |
| 400 | H400 | 지분 배정 요청 정보가 올바르지 않습니다. |
| 401 | G001 | 인증되지 않은 서비스 요청입니다. |
| 403 | G002 | Offering Service만 호출할 수 있습니다. |
| 404 | A404 | 존재하지 않는 자산입니다. |
| 409 | H409 | 발행 가능한 지분 수량을 초과했습니다. |

---

# 수정 API 3. 청약 보상 지분 회수

**Method:** `POST`  
**Full Path:** `/api/v1/internal/holdings/revocations`  
**노션 path 입력값:** `/internal/holdings/revocations`  
**권한:** `SYSTEM`

### 1. 목적 및 요약

모집 미달로 공모가 무효화되거나 관리자가 공모를 중단했을 때, 청약을 기준으로 이미 배정된 지분을 시스템이 회수한다.

기존처럼 URL에서 `holdingId`를 받지 않고 `subscriptionId`로 처리해야 Holding이 없거나 이미 회수된 요청도 멱등하게 성공시킬 수 있다. `REVOKE`는 투자자 간 지분 매도 기능이 아니라 **공모 보상 전용 시스템 회수**를 의미한다.

### 2. 제약사항 및 비즈니스 로직

| 구분 | 항목 | 내용 |
| --- | --- | --- |
| 권한 | 호출 주체 | 인증된 Offering Service 또는 Kafka Consumer 내부 처리만 허용 |
| 회수 기준 | 식별자 | `subscriptionId`로 기존 `ALLOCATE` 이력을 조회 |
| 허용 사유 | 모집 미달 | `UNDER_SUBSCRIBED` |
| 허용 사유 | 공모 중단 | `ADMIN_OFFERING_CANCELLED` |
| 멱등성 | 이미 회수됨 | 수량을 다시 차감하지 않고 `ALREADY_REVOKED` 반환 |
| 멱등성 | Holding 없음 | 오류 대신 `NO_ACTION` 반환 |
| 수량 | 회수 수량 | 해당 `subscriptionId`로 배정된 수량만 회수 |
| 트랜잭션 | 처리 범위 | Holding 수량, 지분 이력, 자산 `allocatedQuantity`를 하나의 트랜잭션으로 감소 |
| 배당 | Snapshot | 회수 완료 시점 이후 Record Date Snapshot에서 회수 수량 제외 |
| Kafka | 결과 이벤트 | 성공·무처리 시 `HoldingRevocationSucceeded`, 실패 시 `HoldingRevocationFailed`를 Outbox로 발행 |

`p_holdings`가 `(assetId, userId)`별 현재 잔액 집계 테이블이라면 Holding 전체에 `REVOKED` 상태를 두지 않는다. 같은 Holding에 다른 정상 청약 수량이 남을 수 있기 때문이다. 청약별 배정·회수 여부는 `p_holding_histories.subscription_id`로 관리한다.

### 3. 입출력 명세

- Request Header 예시

  ```http
  Authorization: Bearer {serviceToken}
  Idempotency-Key: revoke-subscription-018f44aa
  ```

- Request Body 예시

  ```json
  {
    "subscriptionId": "018f44aa-caf4-7d5d-83ec-b926f3d78c90",
    "offeringId": "018f4191-1c2b-7e44-a610-79215060c421",
    "assetId": "018f1c10-2f49-7f72-8d42-8d56ac18f7ad",
    "reason": "UNDER_SUBSCRIBED"
  }
  ```

- Response 예시 — 정상 회수

  ```json
  {
    "result": "REVOKED",
    "holdingId": "018f45a0-8419-75e5-9218-e99e494391ec",
    "subscriptionId": "018f44aa-caf4-7d5d-83ec-b926f3d78c90",
    "revokedQuantity": 10,
    "balanceBefore": 10,
    "balanceAfter": 0,
    "reason": "UNDER_SUBSCRIBED",
    "processedAt": "2026-08-30T13:40:00Z"
  }
  ```

- Response 예시 — Holding 없음

  ```json
  {
    "result": "NO_ACTION",
    "holdingId": null,
    "subscriptionId": "018f44aa-caf4-7d5d-83ec-b926f3d78c90",
    "revokedQuantity": 0,
    "message": "회수할 지분 배정 이력이 없습니다.",
    "processedAt": "2026-08-30T13:40:00Z"
  }
  ```

### 4. 응답 코드

| HTTP Status | code (Custom Code) | message |
| --- | --- | --- |
| 200 |  | 지분 회수 요청이 처리되었습니다. |
| 400 | H400 | 지분 회수 요청 정보 또는 회수 사유가 올바르지 않습니다. |
| 401 | G001 | 인증되지 않은 서비스 요청입니다. |
| 403 | G002 | Offering Service만 호출할 수 있습니다. |
| 409 | H409 | 지분 수량 정합성 문제로 회수할 수 없습니다. |
| 500 | H500 | 지분 회수 처리에 실패했습니다. |

---

## API 외 함께 수정해야 하는 데이터·이벤트 계약

| 항목 | 필수 변경 |
| --- | --- |
| `p_holding_histories` | `(subscription_id, history_type)` UNIQUE 제약 추가 |
| Asset Service Outbox | `p_outbox_events` 추가. 배정·회수 결과 및 수익 이벤트를 DB 커밋과 함께 저장 |
| Asset Service Consumer 멱등성 | `p_processed_events` 또는 동등한 Event ID 저장 구조 추가 |
| 지분 회수 결과 이벤트 | `HoldingRevocationSucceeded`, `HoldingRevocationFailed` 정의 |
| 자산 상세 응답 | `unitPrice`, `totalShareQuantity`, `allocatedQuantity`, `assetStatus`, `version` 포함 |
| Offering Snapshot | 자산 상세 조회 결과의 `unitPrice`, `totalShareQuantity`, `assetVersion`을 공모 생성 시 복제 |
| 삭제 판정 | 기존 합의대로 `is_deleted`가 아니라 `deleted_at IS NULL` 기준으로 통일 필요 |

## 최종 결론

MVP에는 신규 API 4개와 기존 API 3개 수정만 반영하면 된다. 자산 종료·최종 정산 요청 API는 노션 합의대로 도전 기간에 추가한다. Kafka 이벤트를 사용한다는 이유로 배정·회수 REST API와 동일한 비즈니스 로직을 별도로 구현하지 말고, 하나의 Application Service를 REST Controller와 Kafka Consumer가 함께 호출하도록 구성한다.
