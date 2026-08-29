# money-town

[![CI](https://github.com/money-town/money-town-be/actions/workflows/ci.yml/badge.svg)](https://github.com/money-town/money-town-be/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/money-town/money-town-be/branch/main/graph/badge.svg)](https://codecov.io/gh/money-town/money-town-be)

RWA(실물자산) 조각투자 및 배당 정산 플랫폼입니다. Spring Boot 기반 MSA로 구성하며, 선착순 청약의 동시성 제어,
금융 거래 정합성, 대규모 배당 처리, 이상 청약 탐지(FDS), RAG 기반 AI 포트폴리오 추천을 핵심 과제로 다룹니다.
서비스 간 동기 통신은 Feign Client, 비동기 후속 처리는 Kafka 이벤트를 사용합니다.

## 기술 스택

- Java 17, Spring Boot 3.5.16
- Spring Cloud 2025.0.0 (Eureka, Config Server, Gateway)
- PostgreSQL 18.4 + pgvector (서비스별 논리 DB 분리)
- Redis 7.4.7, Kafka 3.9.2
- Spring AI 1.1.8 + Gemini + pgvector RAG
- Slack API (이상 청약 알림)
- Docker Compose, GitHub Actions
- 배포: Amazon EC2(Docker Compose Blue/Green), Amazon RDS, S3, Nginx + Certbot

## 서비스

| 서비스 | 포트 | 역할 |
|---|---:|---|
| discovery-server | 19090 | 서비스 등록·탐색 (Eureka) |
| gateway-service | 19091 | 외부 진입점, 라우팅, CORS/Swagger |
| user-service | 19092 | 인증·회원·KYC·RBAC |
| config-server | 19093 | 중앙 설정 관리 |
| wallet-service | 19094 | 지갑·원장·청약금 동결 |
| asset-service | 19095 | RWA 자산·지분·문서 |
| offering-service | 19096 | 공모·선착순 청약 |
| settlement-service | 19097 | 수익·배당 정산 |
| analysis-service | 19098 | FDS·AI 포트폴리오·알림 |

`common-module`은 포트 없이 공유 라이브러리로만 사용되며(Eureka 미등록), BaseEntity·`ApiResponse<T>`·공통 예외·
FeignExceptionTranslator·Kafka 이벤트 envelope 등 순수 인프라성 코드만 포함합니다. 도메인 로직/엔티티/DTO는 포함하지
않으며, 변경 시 의존하는 모든 서비스의 재빌드·재배포가 필요합니다.

## 로컬 실행

1. `.env.example`을 `.env`로 복사하고 값을 채웁니다. `POSTGRES_PASSWORD`/`REDIS_PASSWORD`는 평문 기본값이 없으므로
   반드시 직접 설정해야 합니다.
2. 각 서비스의 JAR을 빌드합니다 (Dockerfile이 `build/libs/*.jar`를 COPY).
3. Docker Compose로 인프라와 서비스를 함께 기동합니다.

```bash
./gradlew clean bootJar
docker compose --env-file .env -f infrastructure/docker-compose.yml up -d
docker compose --env-file .env -f infrastructure/docker-compose.yml ps
```

- 기동 순서 의존성: `discovery-server → config-server → (gateway-service, 도메인 서비스들)` — `depends_on`/healthcheck로
  이미 반영되어 있습니다.
- 종료: `docker compose --env-file .env -f infrastructure/docker-compose.yml down` (`-v` 추가 시 DB/Redis/Kafka 볼륨도 삭제)

## 확인 URL

- Eureka: http://localhost:19090
- Config Server health: http://localhost:19093/actuator/health
- Gateway (Swagger UI): http://localhost:19091/swagger-ui.html
- Nginx: http://localhost:8002
- Kafka UI: http://localhost:8081
- RedisInsight: http://localhost:8001
- Zipkin: http://localhost:9411/zipkin/

## 개발 원칙

- 각 서비스는 자신의 DB만 접근합니다 (Database per Service). 서비스 간 FK 직접 참조를 금지합니다.
- 서비스 간 동기 호출은 Feign Client, 비동기 후속 처리는 Kafka 이벤트를 사용합니다.
- MVP 필수 기능을 먼저 구현하고, 트러블슈팅(도전) 범위는 별도로 표시해 이후 진행합니다.
- Secret은 `.env`(로컬) 또는 AWS Parameter Store/Secrets Manager(배포 환경)에서 관리하며, `config-repository`에는
  평문 비밀번호를 두지 않습니다.
- `dev`(기본 개발) → `main`(배포) 브랜치 전략을 따르며, `feature/`·`fix/`·`docs/` 접두사를 사용하고 `main`/`dev`에는
  리뷰 승인 1건 이상을 받은 Pull Request로만 병합합니다.