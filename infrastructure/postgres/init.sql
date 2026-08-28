CREATE DATABASE user_db;
CREATE DATABASE wallet_db;
CREATE DATABASE asset_db;
CREATE DATABASE offering_db;
CREATE DATABASE settlement_db;
CREATE DATABASE analysis_db;

-- analysis-service의 RAG(Vector DB) 기능에만 필요한 확장
\connect analysis_db
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";