CREATE DATABASE user_db;
CREATE DATABASE wallet_db;
CREATE DATABASE asset_db;
CREATE DATABASE offering_db;
CREATE DATABASE settlement_db;
CREATE DATABASE analysis_db;

-- 서비스별 느린 쿼리/부하 원인 쿼리 실측용. 서버 전역 공유메모리에 통계가 쌓이므로
-- 기본 postgres DB 하나에만 만들어도 pg_stat_statements 뷰에서 dbid로
-- user/wallet/asset/offering/settlement/analysis DB 전체 쿼리를 조회할 수 있다.
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- analysis-service의 RAG(Vector DB) 기능에만 필요한 확장
\connect analysis_db
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";