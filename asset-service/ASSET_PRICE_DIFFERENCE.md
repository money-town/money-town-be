# 자산 단가와 차액

## 확정 규칙

- 지분 단가 = 평가금액 / 전체 지분 수량의 소수점 이하 버림.
- 차액 = 평가금액 - (지분 단가 × 전체 지분 수량).
- 차액은 0 이상이며, 소유주 채무·납부금·이자로 취급하지 않는다.
- 자산 등록 시 `p_assets.rounding_difference_amount`(BIGINT, NOT NULL)에 계산 결과를 저장한다.
- 지분 단가는 최소 1원이어야 한다.
- 차액은 자산 전체 지분 수량 기준이다. 개별 공모 모집액이나 실제 매각대금과는 구분한다.
- 실제 지갑 출금, 플랫폼 수납, 매각대금 공제는 수행하지 않는다.

예: 평가금액 100,001,000원 / 지분 10,000개 → 단가 10,000원, 차액 1,000원.

## API 변경

### POST /api/v1/assets

`unitPrice`, `roundingDifferenceAmount`는 서버에서 계산한다. 요청에 넣지 않는다.
이전의 `ownerBurdenPaymentMethod` 필드는 제거한다.

```json
{
  "assetName": "강남 업무용 빌딩",
  "type": "REAL_ESTATE",
  "description": "업무용 부동산",
  "valuationAmount": 100001000,
  "expectedReturnRate": 5.25,
  "detailData": {},
  "totalShareQuantity": 10000
}
```

등록 응답은 기존의 assetId, assetName, assetStatus, createdAt 구조를 유지한다.

### GET /api/v1/assets/{assetId}

상세 응답에 `roundingDifferenceAmount`를 포함한다.
`ownerBurdenPaymentMethod`, `ownerBurdenAmount`, `offeringCompletedAt`은 제거한다.

응답 data 중 관련 필드 예시:

```json
{
  "valuationAmount": 100001000,
  "unitPrice": 10000,
  "totalShareQuantity": 10000,
  "roundingDifferenceAmount": 1000
}
```

### 제거한 API

- POST /api/v1/internal/assets/{assetId}/offering-completion
- GET /api/v1/internal/assets/{assetId}/owner-burden/quote

차액·이자 기능 때문에 추가했던 API만 제거한다. 기존 공모용 자산 조회와 지분 API는 유지한다.
정산·지갑 서비스에 차액 수납을 요청하지 않는다.

## DB 변경

아직 DB를 생성하지 않은 상태이므로 `V1__baseline.sql` 하나에 최종 테이블과 인덱스를 정의한다.
V2~V5는 제거했으며 이자·납부 관련 컬럼은 처음부터 생성하지 않는다.
`rounding_difference_amount`는 BIGINT NOT NULL이고, 차액이 0 이상이며 계산식과 일치하는지 CHECK 제약으로 검증한다.
평가금액·단가·전체 수량을 변경하는 기능을 추가할 때는 차액도 함께 재계산해야 한다.
