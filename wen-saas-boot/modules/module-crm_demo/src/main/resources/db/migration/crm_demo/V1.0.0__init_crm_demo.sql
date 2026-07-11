-- crm_demo 业务模块（Demo）· 客户表
-- 模块编码 crm_demo；表前缀 crm_demo_（SPI ModuleTableValidator 启动期校验）。
--
-- Flyway 分模块约定：本模块迁移独占目录 db/migration/crm_demo/，由 ModuleSchemaMigrator 用独立
-- history 表 flyway_history_crm_demo 执行；版本号仅在模块内唯一、从 V1.0.0 起递增，与底座/他模块无关。

CREATE TABLE crm_demo_customer (
    id               BIGINT       NOT NULL PRIMARY KEY,        -- 雪花 ID
    tenant_id        BIGINT       NOT NULL DEFAULT 0,
    name             VARCHAR(255) NOT NULL,
    code             VARCHAR(64),
    status           VARCHAR(20)  NOT NULL DEFAULT 'LEAD',     -- LEAD/FOLLOWING/WON/LOST（标准字段，承载状态机）
    owner_id         BIGINT,                                   -- 负责人 user_id（「我的客户」业务筛选）
    owner_dept_id    BIGINT,                                   -- 归属部门（数据权限 DEPT_AND_BELOW 列）
    creator_id       BIGINT       NOT NULL,                    -- 创建人（数据权限 SELF 列）
    extra            JSONB,                                    -- 自定义字段值容器（@CustomFieldExtension）
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL,
    deleted_at       TIMESTAMP,                                -- 软删（@TableLogic）
    source           VARCHAR(64),                              -- 客户来源（标准字段，存字典项 code；字典分组 crm_demo.customer_source 由 SPI 预置）
    stage_changed_at TIMESTAMP    NOT NULL                     -- 阶段进入时间：建档/推进阶段时写入，普通编辑不刷新
);

COMMENT ON TABLE  crm_demo_customer IS 'CRM 客户（Demo 业务模块：演示自定义字段 / 列偏好 / 数据权限三能力）';
COMMENT ON COLUMN crm_demo_customer.extra IS '自定义字段 jsonb 容器，key 为 cf_ 前缀字段编码';
COMMENT ON COLUMN crm_demo_customer.source IS '客户来源：字典分组 crm_demo.customer_source 的项 code；NULL=未填';
-- 「阶段停留超期提醒」Job（CrmDemoStageStaleReminder）按 stage_changed_at 精确计算停留时长（updated_at 会被编辑刷新，不可用）
COMMENT ON COLUMN crm_demo_customer.stage_changed_at IS '进入当前阶段的时间（建档/推进时写，编辑不刷新）；停留超期提醒的扫描依据';

-- 索引前缀必须 tenant_id；活跃记录用 partial index 跳过软删
CREATE INDEX idx_crm_demo_customer_tenant_status ON crm_demo_customer (tenant_id, status)        WHERE deleted_at IS NULL;
CREATE INDEX idx_crm_demo_customer_tenant_owner  ON crm_demo_customer (tenant_id, owner_id)      WHERE deleted_at IS NULL;
CREATE INDEX idx_crm_demo_customer_tenant_dept   ON crm_demo_customer (tenant_id, owner_dept_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_crm_demo_customer_tenant_source ON crm_demo_customer (tenant_id, source)        WHERE deleted_at IS NULL;
-- 阶段停留超期扫描：status IN (LEAD, FOLLOWING) AND stage_changed_at < 阈值
CREATE INDEX idx_crm_demo_customer_stage_stale   ON crm_demo_customer (tenant_id, status, stage_changed_at) WHERE deleted_at IS NULL;
