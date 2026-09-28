# 🏙️ money-town

[![CI](https://github.com/money-town/money-town-be/actions/workflows/ci.yml/badge.svg)](https://github.com/money-town/money-town-be/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/money-town/money-town-be/branch/main/graph/badge.svg)](https://codecov.io/gh/money-town/money-town-be)

---

## 🖐️ 팀 구성

| **한승욱** | **강소율**  | **김태희**| **나상우** | **차은지** | **김민지** |
|----------------------------------------|---|---|---|---|---|
| <div align="center"><img src="https://github.com/hanwoo7726.png" width="150"/><br/>[@hanwoo7726](https://github.com/hanwoo7726)</div> | <div align="center"><img src="https://github.com/soyul9280.png" width="150"/><br/>[@soyul9280](https://github.com/soyul9280)</div> | <div align="center"><img src="https://github.com/TaeHeeKim98.png" width="150"/><br/>[@TaeHeeKim98](https://github.com/TaeHeeKim98)</div> | <div align="center"><img src="https://github.com/nadaasw.png" width="150"/><br/>[@nadaasw](https://github.com/nadaasw)</div> |<div align="center"><img src="https://github.com/ztoacej.png" width="150"/><br/>[@ztoacej](https://github.com/ztoacej)</div> |<div align="center"><img src="https://github.com/poppq03.png" width="150"/><br/>[@poppq03](https://github.com/poppq03)</div> |

---

## 💡 프로젝트 소개

> 🏢 실물자산(RWA), 이제 조각으로 투자하세요.
>
> money-town은 부동산·음원 저작권 같은 실물자산을 지분 단위로 쪼개 투자하고, 발생한 수익을 지분율대로 정확히
> 배당받는 조각투자 플랫폼입니다. 선착순 청약의 동시성 제어, 금융 거래 정합성, 대규모 배당 처리, 이상 청약
> 탐지(FDS), LLM 기반 AI 포트폴리오 추천을 핵심 과제로 다룹니다.

- RWA(실물자산) 조각투자 및 배당 정산 SaaS
- Spring Boot 기반 MSA, 서비스 간 동기 통신은 Feign Client, 비동기 후속 처리는 Kafka 이벤트

### 📌 프로젝트 정보

| 항목 | 내용                         |
|---|----------------------------|
| **📆 프로젝트 기간** | 2026.08.25 ~ 2026.09.28    |
| **🔗 배포 링크** | https://www.moneytown.shop |
| **🎬 시연 영상** |  [시연 영상](https://drive.google.com/file/d/1s3ViN5XZVm_tsH-eOvjJu9KlD8uJSL7O/view?usp=drive_link)                          |
| **📑 발표 자료** |  [발표 자료](https://docs.google.com/presentation/d/11AwnJ5f7cQOf3NaIYF7t0QEVCiKctk0y/edit?usp=drive_link&ouid=106430970059448745336&rtpof=true&sd=true)                          |

### 🏗️ 제공 기능

| 서비스 | 상세 내용 |
|---|---|
| **user-service**<br/>(인증·회원·KYC) | - 회원가입/로그인, JWT 발급·재발급, 로그아웃<br/>- `INVESTOR`/`ISSUER`/`ADMIN` 역할 기반 RBAC 원본 제공<br/>- Mock KYC 신청·심사, 투자 가능 상태(`role`·`kycStatus`·`accountStatus`·`investmentEligibilityStatus`) 판단<br/>- 다른 서비스가 호출하는 내부 사용자 검증 API |
| **wallet-service**<br/>(지갑·원장) | - 가입 시 지갑 자동 생성, 예치금 입출금<br/>- 청약금 동결/해제/최종 차감 생애주기 제어<br/>- append-only 원장 + 비관적 락 기반 잔액 동시성 제어<br/>- 멱등키 기반 중복 입출금 차단, 배당·정산금 입금 수신 |
| **asset-service**<br/>(RWA 자산·지분) | - 부동산·음원 저작권 자산 등록·심사(승인/반려)<br/>- S3 기반 투자설명서·감정평가서·증빙자료 업로드, 버전·해시 관리<br/>- Redis 자산 캐싱, 임대료·저작권료 등 수익 데이터를 공통 Revenue 형식으로 표준화 후 Kafka 이벤트 발행<br/>- LLM 기반 Synthetic Data·자산 설명 생성 |
| **offering-service**<br/>(공모·청약) | - 공모 상품 등록·심사·모집 상태 관리<br/>- DB 조건부 UPDATE 기반 원자적 수량 차감으로 선착순 청약 동시성 제어<br/>- Outbox 기반 비동기 청약 처리(지갑 동결 → 성공/실패 상태 반영, 실패 시 수량 복원) |
| **settlement-service**<br/>(수익·배당 정산) | - 자산 수익 확정 이벤트 수신 → 배당 회차 개설, 최대잔여법으로 지분율 배당 계산(잔여금 0원 보장)<br/>- Outbox+Kafka 기반 배당 지급, 지갑 입금 결과를 이벤트로 수신해 상태 반영<br/>- 실패 지급 건 관리자 포기 처리, 미해결 회차 자동 재통보·에스컬레이션<br/>- 자산 종료 시 최종 정산(원금 반환) |
| **analysis-service**<br/>(FDS·AI·알림) | - Redis 기반 사용자 행동 데이터로 Pre-FDS 이상 청약 사전 차단, Post-FDS Kafka 이벤트 기반 사후 재검사<br/>- pgvector RAG로 RWA 상품 임베딩 후 LLM 기반 AI 포트폴리오 추천<br/>- Kafka 알림 이벤트 구독 → Slack 알림 발송 |

---

## ⚙️ 기술 스택

### 🖥️ 언어

![Java](https://img.shields.io/badge/Java-17-007396?style=for-the-badge&logo=openjdk&logoColor=white)

### 🧩 백엔드

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.16-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-Eureka·Config·Gateway-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Spring Data JPA](https://img.shields.io/badge/Spring_Data_JPA-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring%20Security-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white)
![QueryDSL](https://img.shields.io/badge/QueryDSL-0769AD?style=for-the-badge&logo=java&logoColor=white)
![Resilience4j](https://img.shields.io/badge/Resilience4j-DB1F48?style=for-the-badge&logoColor=white)
![Spring AI](https://img.shields.io/badge/Spring%20AI-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![OpenAI](https://img.shields.io/badge/OpenAI%20gpt--4o--mini-412991?style=for-the-badge&logo=openai&logoColor=white)
![Testcontainers](https://img.shields.io/badge/Testcontainers-333?style=for-the-badge&logo=docker&logoColor=white)
![JUnit5](https://img.shields.io/badge/JUnit5-25A162?style=for-the-badge&logo=junit5&logoColor=white)

### 🔩 데이터/메시징

![PostgreSQL](https://img.shields.io/badge/PostgreSQL%2018-316192?style=for-the-badge&logo=postgresql&logoColor=white)
![pgvector](https://img.shields.io/badge/pgvector-316192?style=for-the-badge&logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white)
![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-000?style=for-the-badge&logo=apachekafka&logoColor=white)

### 🧑‍🔧 인프라·운영

![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![AWS EC2](https://img.shields.io/badge/Amazon%20EC2-FF9900?style=for-the-badge&logo=amazonec2&logoColor=white)
![Amazon ECR](https://img.shields.io/badge/Amazon%20ECR-FF9900?style=for-the-badge&logo=amazon-aws&logoColor=white)
![Amazon S3](https://img.shields.io/badge/Amazon%20S3-FF9900?style=for-the-badge&logo=amazons3&logoColor=white)
![Nginx](https://img.shields.io/badge/nginx-009639?style=for-the-badge&logo=nginx&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/GitHub%20Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white)
![Prometheus](https://img.shields.io/badge/Prometheus-E6522C?style=for-the-badge&logo=prometheus&logoColor=white)
![Grafana](https://img.shields.io/badge/Grafana-F46800?style=for-the-badge&logo=grafana&logoColor=white)
![Zipkin](https://img.shields.io/badge/Zipkin-FF6600?style=for-the-badge&logoColor=white)

### 기타

![Slack](https://img.shields.io/badge/Slack%20API-4A154B?style=for-the-badge&logo=slack&logoColor=white)
![Swagger](https://img.shields.io/badge/Swagger-85EA2D?style=for-the-badge&logo=swagger&logoColor=black)
![Codecov](https://img.shields.io/badge/Codecov-F01F7A?style=for-the-badge&logo=codecov&logoColor=white)
![CodeRabbit](https://img.shields.io/badge/CodeRabbit-FF070A?style=for-the-badge&logoColor=white)
![GitHub](https://img.shields.io/badge/github-121011?style=for-the-badge&logo=github&logoColor=white)
![Notion](https://img.shields.io/badge/Notion-000000?style=for-the-badge&logo=notion&logoColor=white)

---

## 👥 팀원 R&R

| 팀원      | 역할 및 기여 |
|---------|---|
| **한승욱** | **사용자(user-service) 도메인 담당**<br/>- 회원·인증, Redis JTI·TTL 기반 Refresh Token, Lua 스크립트로 동시 재발급 방지<br/>- KYC·발행자 심사, BCrypt 동시 실행 제한으로 로그인 CPU 과부하 방지<br/>**게이트웨이(gateway-service) 담당**<br/>- JWT 검증 및 역할별(INVESTOR·ISSUER·ADMIN) API 접근 제어, 내부 API 외부 차단<br/>- Correlation ID 기반 서비스 간 요청 추적, Nginx 기반 Gateway 요청 분산 |
| **김태희** | **자산(asset-service) 도메인 담당**<br/>- 자산 등록·심사·수익 관리, 배당 기준일 지분 스냅샷 계산·제공<br/>- Kafka·Outbox 기반 지분 배정·회수, 자산 종료·수익 등록 이벤트 발행<br/>- Holding → Asset 락 순서로 교착 제어, 커서 페이지네이션·Redis 캐시로 조회 성능 개선 |
| **차은지** | **공모/청약(offering-service) 도메인 담당**<br/>- 공모 등록·심사·상태 전이, 선착순 청약 접수·확정<br/>- 코레오그래피 사가 설계, Outbox 기반 이벤트 발행 및 멈춘 이벤트 자동 복구<br/>- 원자적 조건부 UPDATE·Idempotency-Key 해시 검증으로 동시성·중복 요청 제어 |
| **나상우** | **이상탐지(analysis-service) 도메인 담당**<br/>- 청약 이상거래 탐지(Pre/Post FDS), AI 포트폴리오 추천<br/>- 조건부 UPDATE(CAS)·event_id 기반 중복 처리로 상태 경합·오탐 방지<br/>**모니터링·알림 공통 담당**<br/>- Prometheus/Grafana 공통 대시보드 구축, Slack 알림 인프라(FDS 차단·CPU 경보) 구축 |
| **김민지** | **지갑(wallet-service) 도메인 담당**<br/>- 예치금 입출금, 청약금 동결·해제·차감, 배당·정산금 입금 및 모집미달·취소 보상 처리<br/>- 비관적 락·append-only 원장·멱등키로 거래 정합성 보장, 배당 지급 Kafka 이벤트 소비 전환<br/>- Redis 캐싱·인프라 튜닝으로 조회 API P95 2.2배 개선, 라인 커버리지 90.7% 달성 |
| **강소율** | **정산(settlement-service) 도메인 담당**<br/>- 배당/최종 정산 파이프라인, 최대잔여법 배분 계산, 실패 지급 복구·재통보 설계<br/>- Kafka 기반 회차 개시·배당 지급 이벤트 전환<br/>**인프라·공통설정 담당**<br/>- EC2/Docker Compose 기반 배포 아키텍처 설계, CI/CD 파이프라인(GitHub Actions) 구축<br/>- ECR 도입, 컨테이너 계단식 기동 최적화, 부하테스트(JMeter) 기반 병목 분석 및 개선 |

---

## 🏢 아키텍처

<details>
<summary>📁 프로젝트 구조 보기</summary>

```
money-town
├─ build.gradle / settings.gradle       # 루트 Gradle 설정
├─ config-repository/                   # 서비스별 application.yml (Config Server가 서빙)
├─ infrastructure/
│  ├─ Dockerfile                        # 전 서비스 공용 (SERVICE 빌드 인자로 분기)
│  ├─ docker-compose.yml                # base
│  ├─ docker-compose.prod.yml           # 운영 오버레이 (포트 폐쇄 등)
│  ├─ docker-compose.ecr.yml            # ECR 이미지 사용 오버레이
│  ├─ nginx/ · postgres/ · grafana/ · prometheus/ · seed/
│
├─ discovery-server/                    # Eureka Server (Eureka 미등록)
├─ config-server/                       # Spring Cloud Config Server
├─ gateway-service/                     # API Gateway, JWT 1차 검증, 라우팅
├─ common-module/                       # 공유 라이브러리 (포트 없음, Eureka 미등록)
│  └─ .../common/{security,exception,event,response}/
│
├─ user-service/                        # 인증·회원·KYC·RBAC
├─ wallet-service/                      # 지갑·원장·청약금 동결
├─ asset-service/                       # RWA 자산·지분·문서(S3)
├─ offering-service/                    # 공모·선착순 청약
├─ settlement-service/                  # 수익·배당 정산 (CQRS 스타일 구조)
└─ analysis-service/                    # FDS·AI 포트폴리오·알림
```

> `common-module`은 BaseEntity·`ApiResponse<T>`·공통 예외·FeignExceptionTranslator·Kafka 이벤트 envelope 등
> 순수 인프라성 코드만 포함하며 도메인 로직/엔티티/DTO는 포함하지 않습니다.

</details>

### ⚙️ 배포 아키텍처

<img width="2154" height="1692" alt="3-current-staggered" src="https://github.com/user-attachments/assets/b0ddff18-06c2-4431-ada4-b425e9c044e8" />


### 📔 데이터베이스 ERD

 [ERD 링크](https://www.erdcloud.com/d/eixJ59jmx4AKtJpkq)에서 확인할 수 있습니다
