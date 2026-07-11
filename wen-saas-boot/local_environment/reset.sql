-- ============================================================================
-- 数据库彻底重置脚本（dev）
--
-- 用途：清空 wen_saas 数据库的 public schema，让 Flyway 在"真正空库"上从 V1.0.0 跑起。
--
-- 背景：之前只 DROP 各业务表、未清干净（残留序列/类型/flyway_schema_history 等），
-- schema 仍被 Flyway 判为"非空" → baseline-on-migrate 自动打 baseline=版本1，
-- 而 Flyway 把 "1" 与 "1.0.0" 视为同一版本 → V1.0.0__init_schema.sql 被当成已包含在
-- 基线里直接跳过 → 后续迁移引用 sys_tenant 时报 relation does not exist。
--
-- DROP SCHEMA ... CASCADE 一次性清掉所有表/序列/函数/触发器/类型，schema 真正为空，
-- Flyway 不再触发 baseline，从 V1.0.0 正常执行。
--
-- 用法（任选其一）：
--   psql -h 127.0.0.1 -U postgres -d wen_saas -f reset.sql
--   或在 DataGrip / IDEA 数据库控制台对 wen_saas 库执行本文件
-- 然后重启应用，Flyway 依次执行 V1.0.0__init_schema.sql、V1.0.1__init_data.sql。
-- ============================================================================

DROP SCHEMA public CASCADE;
CREATE SCHEMA public;

-- Quartz 调度表在独立 quartz schema（由迁移建），一并清掉让 Flyway 重建
DROP SCHEMA IF EXISTS quartz CASCADE;

-- 还原默认授权（PostgreSQL 15+ 默认不再对 public 角色开放 CREATE，按需放开 dev 使用）
GRANT ALL ON SCHEMA public TO CURRENT_USER;
GRANT ALL ON SCHEMA public TO public;
