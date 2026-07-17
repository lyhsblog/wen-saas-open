-- ============================================================================
-- 套餐包来源模块（V1.0.3）
--
-- 业务模块可通过 SPI（BusinessModuleDescriptor#packages）声明预置套餐包，启动期由
-- PackageSpiSynchronizer 以 insert-if-missing 语义落库。source_module 标记该套餐由
-- 哪个业务模块声明：NULL=运营手工创建；非 NULL 的套餐不可物理删除（防止重启后被
-- 同步器重新插入而"复活"），仅可下架/归档。
-- ============================================================================

ALTER TABLE sys_package ADD COLUMN source_module VARCHAR(64);

COMMENT ON COLUMN sys_package.source_module IS '来源业务模块编码（SPI 声明的预置套餐）；NULL=运营手工创建。非 NULL 不可物理删除，仅可下架/归档';

CREATE INDEX idx_sys_package_source_module ON sys_package (source_module);

-- 审计列元数据（对齐 V1.0.1 第 0 节写法）
INSERT INTO sys_audit_table_config(table_name, column_name, label, render) VALUES
    ('sys_package', 'source_module', '来源模块', 'SCALAR')
ON CONFLICT (table_name, column_name) DO NOTHING;
