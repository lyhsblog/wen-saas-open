-- 仅供 ModuleSchemaMigratorIT 验证"每模块独立 Flyway"机制（非真实模块）。
CREATE TABLE IF NOT EXISTS itmod_probe (
    id        BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    note      VARCHAR(64)
);
