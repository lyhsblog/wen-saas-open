-- ============================================================================
-- Wen SaaS Boot 全量基线 Schema（V1.0.0）
--
--   · 全部表结构（含月度 RANGE 分区表）、序列、索引、COMMENT、审计触发器函数与挂载；
--   · 种子数据（字典/套餐/账号/角色/菜单/参数/通知模板/审计配置）见 V1.0.1__init_data.sql；
--   · Quartz 集群表（独立 quartz schema）见 V1.0.2__quartz_cluster_tables.sql。
--
-- 审计列元数据（sys_audit_table_config 的 MASK/EXCLUDE 规则）由 V1.0.1 最先写入，
-- 使初始化数据触发审计时脱敏/排除规则即时生效。
-- ============================================================================


-- ============================================================================
-- 1) 核心平台表
-- ============================================================================

-- 租户主表（纯平台表，无 tenant_id）
CREATE TABLE sys_tenant
(
    id                            BIGINT       NOT NULL,
    code                          VARCHAR(64)  NOT NULL,
    name                          VARCHAR(128) NOT NULL,
    status                        SMALLINT     NOT NULL DEFAULT 1,
    main_admin_user_id            BIGINT,
    logo_url                      VARCHAR(512),
    description                   TEXT,
    -- 套餐授权
    plans                         JSONB        NOT NULL DEFAULT '[]'::JSONB,
    -- 运营开通
    tenant_no                     VARCHAR(32),
    remark                        TEXT,
    -- 租户级有效期
    expire_at                     TIMESTAMPTZ,
    -- CSM / 配额 / 时区
    csm_user_id                   BIGINT,
    member_cap                    INT,
    storage_cap                   BIGINT,
    timezone                      VARCHAR(64),
    -- 公司扩展信息
    full_name                     VARCHAR(128),
    uscc                          VARCHAR(32),
    industry                      VARCHAR(64),
    scale                         VARCHAR(32),
    region                        VARCHAR(128),
    tags                          JSONB,
    -- 自助公司信息
    code_changed_at               TIMESTAMPTZ,
    logo_file_id                  VARCHAR(64),
    -- 防截屏水印开关
    screenshot_watermark_enabled  BOOLEAN,
    created_at                    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                    BIGINT,
    updated_at                    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                    BIGINT,
    deleted_at                    TIMESTAMP,
    CONSTRAINT pk_sys_tenant PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_tenant IS '租户主表（纯平台表，不带 tenant_id）';
COMMENT ON COLUMN sys_tenant.id IS '雪花 ID';
COMMENT ON COLUMN sys_tenant.code IS '租户编码，作为租户子域名前缀（{code}.基础域）';
COMMENT ON COLUMN sys_tenant.status IS '0=暂停 / 1=正常 / 2=已过期';
COMMENT ON COLUMN sys_tenant.main_admin_user_id IS '主管理员 user_id';
COMMENT ON COLUMN sys_tenant.deleted_at IS '软删除时间戳；NULL 表示未删除';
COMMENT ON COLUMN sys_tenant.plans IS '租户已勾选套餐包数组（jsonb）。每元素 {packageCode, grantedAt, expiresAt, revokedAt}';
COMMENT ON COLUMN sys_tenant.tenant_no IS '运营内部编号（运营端可见，系统自动生成，可改；全局唯一）';
COMMENT ON COLUMN sys_tenant.remark IS '运营备注（内部可见，不对租户方展示）';
COMMENT ON COLUMN sys_tenant.expire_at IS '租户级有效期（NULL=永久）；TenantExpireJob 据此自动置 status=2';
COMMENT ON COLUMN sys_tenant.csm_user_id IS '客户经理（运营账号 tenant_id=0 的 sys_user.id，非必填）';
COMMENT ON COLUMN sys_tenant.member_cap IS '成员数上限（NULL=不限制）';
COMMENT ON COLUMN sys_tenant.storage_cap IS '存储上限（字节，NULL=不限制）';
COMMENT ON COLUMN sys_tenant.timezone IS '租户时区 IANA 标识（NULL=按平台默认）';
COMMENT ON COLUMN sys_tenant.full_name IS '公司全称';
COMMENT ON COLUMN sys_tenant.uscc IS '统一社会信用代码';
COMMENT ON COLUMN sys_tenant.industry IS '行业';
COMMENT ON COLUMN sys_tenant.scale IS '规模';
COMMENT ON COLUMN sys_tenant.region IS '所在地区';
COMMENT ON COLUMN sys_tenant.tags IS '标签（字符串数组 jsonb）';
COMMENT ON COLUMN sys_tenant.code_changed_at IS '子域名（code）最近一次修改时间；30 天内最多改一次。NULL=从未改过';
COMMENT ON COLUMN sys_tenant.logo_file_id IS '公司 Logo 文件 id（sys_file.file_id 对外字符串）；NULL=底座默认 Logo';
COMMENT ON COLUMN sys_tenant.screenshot_watermark_enabled IS '防截屏水印是否开启；NULL=默认开启（可溯源底纹水印，租户级开关）';

CREATE UNIQUE INDEX uk_sys_tenant_code_alive
    ON sys_tenant (code)
    WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uk_sys_tenant_no_alive
    ON sys_tenant (tenant_no)
    WHERE deleted_at IS NULL AND tenant_no IS NOT NULL;
CREATE INDEX idx_sys_tenant_csm
    ON sys_tenant (csm_user_id)
    WHERE csm_user_id IS NOT NULL;


-- 业务模块元数据（纯平台表）
CREATE TABLE sys_business_module
(
    id          BIGINT       NOT NULL,
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(128) NOT NULL,
    version     VARCHAR(32)  NOT NULL,
    description TEXT,
    icon        VARCHAR(256),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sys_business_module PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_business_module IS '业务模块元数据（纯平台表）。SPI 注册器在启动期写入/更新';
COMMENT ON COLUMN sys_business_module.code IS '模块编码（如 "crm"），全局唯一';
COMMENT ON COLUMN sys_business_module.version IS '语义化版本';

CREATE UNIQUE INDEX uk_sys_business_module_code
    ON sys_business_module (code);


-- ============================================================================
-- 2) 用户主表
-- ============================================================================
CREATE TABLE sys_user
(
    id            BIGINT       NOT NULL,
    tenant_id     BIGINT       NOT NULL DEFAULT 0,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    real_name     VARCHAR(64)  NOT NULL,
    -- 手机号/邮箱确定性 AES 加密，列宽按密文放宽
    mobile        VARCHAR(128),
    email         VARCHAR(512),
    gender        SMALLINT,
    birthday      DATE,
    avatar_file_id VARCHAR(64),
    emp_no        VARCHAR(32),
    -- 岗位
    position_code VARCHAR(80),
    status        SMALLINT     NOT NULL DEFAULT 1,
    last_login_at TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by    BIGINT,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    BIGINT,
    deleted_at    TIMESTAMP,
    CONSTRAINT pk_sys_user PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_user IS '用户主表（混合表）。tenant_id=0 表示运营账号；tenant_id>0 表示该租户成员';
COMMENT ON COLUMN sys_user.id IS '雪花 ID';
COMMENT ON COLUMN sys_user.tenant_id IS '租户 ID。0=运营/平台账号';
COMMENT ON COLUMN sys_user.username IS '登录账号：字母开头、字母数字 - _，4-16 位';
COMMENT ON COLUMN sys_user.password_hash IS 'BCrypt 密码哈希';
COMMENT ON COLUMN sys_user.mobile IS '手机号密文。确定性 AES-256-GCM，格式 <keyId>:Base64(IV||ct||tag)';
COMMENT ON COLUMN sys_user.email IS '邮箱密文。确定性 AES-256-GCM，格式 <keyId>:Base64(IV||ct||tag)';
COMMENT ON COLUMN sys_user.gender IS '性别。0=未知 / 1=男 / 2=女（选填，自助修改）';
COMMENT ON COLUMN sys_user.birthday IS '生日（选填，自助修改）';
COMMENT ON COLUMN sys_user.avatar_file_id IS '头像文件 ID（sys_file.fileId）；展示时换签名 URL';
COMMENT ON COLUMN sys_user.emp_no IS '员工编号（如 EMP-0001）；由超管在团队成员页设定，成员只读';
COMMENT ON COLUMN sys_user.position_code IS '岗位（字典 POSITION 项 code 快照；单选非必填）';
COMMENT ON COLUMN sys_user.status IS '0=停用 / 1=正常';
COMMENT ON COLUMN sys_user.last_login_at IS '最近一次登录成功时间';
COMMENT ON COLUMN sys_user.deleted_at IS '软删除时间戳；NULL 表示未删除';

CREATE UNIQUE INDEX uk_sys_user_tenant_username_alive
    ON sys_user (tenant_id, username)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_sys_user_tenant_mobile
    ON sys_user (tenant_id, mobile)
    WHERE deleted_at IS NULL AND mobile IS NOT NULL;
CREATE INDEX idx_sys_user_tenant_email
    ON sys_user (tenant_id, email)
    WHERE deleted_at IS NULL AND email IS NOT NULL;
-- 审计「对象」按 real_name 精确命中 sys.user（成员允许重名，非唯一）
CREATE INDEX idx_sys_user_tenant_real_name
    ON sys_user (tenant_id, real_name)
    WHERE deleted_at IS NULL AND real_name IS NOT NULL;

-- 用户级列表列偏好。底座通用：任意列表页按 view_key 各存一份增量
-- （custom_keys=显式可见列有序，hidden_keys=显式隐藏列）；重置=删行，保持稀疏。
CREATE TABLE sys_user_view_pref
(
    id          BIGINT       NOT NULL,
    tenant_id   BIGINT       NOT NULL DEFAULT 0,
    user_id     BIGINT       NOT NULL,
    view_key    VARCHAR(120) NOT NULL,
    custom_keys JSONB        NOT NULL DEFAULT '[]',
    hidden_keys JSONB        NOT NULL DEFAULT '[]',
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    CONSTRAINT pk_sys_user_view_pref PRIMARY KEY (id),
    CONSTRAINT uq_user_view_pref UNIQUE (tenant_id, user_id, view_key)
);

COMMENT ON TABLE sys_user_view_pref IS '用户级列表列偏好（增量存储）';


-- ============================================================================
-- 3) 权限引擎
-- ============================================================================
CREATE TABLE sys_role
(
    id          BIGINT       NOT NULL,
    tenant_id   BIGINT       NOT NULL DEFAULT 0,
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(128) NOT NULL,
    description TEXT,
    status      SMALLINT     NOT NULL DEFAULT 1,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  BIGINT,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  BIGINT,
    deleted_at  TIMESTAMP,
    CONSTRAINT pk_sys_role PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_role IS '角色（混合表）。tenant_id=0 为运营角色；>0 为租户角色。数据范围见 sys_role_data_scope';
COMMENT ON COLUMN sys_role.status IS '0=停用 / 1=启用';

CREATE UNIQUE INDEX uk_sys_role_tenant_code_alive
    ON sys_role (tenant_id, code)
    WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uk_sys_role_tenant_name_alive
    ON sys_role (tenant_id, name)
    WHERE deleted_at IS NULL;


CREATE TABLE sys_role_permission
(
    id              BIGINT       NOT NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 0,
    role_id         BIGINT       NOT NULL,
    permission_code VARCHAR(128) NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT,
    CONSTRAINT pk_sys_role_permission PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_role_permission IS '角色 → 权限点绑定（混合表）';
COMMENT ON COLUMN sys_role_permission.permission_code IS '权限点编码，对应 PermissionDef.code（不强制外键）';

CREATE UNIQUE INDEX uk_sys_role_permission_role_code
    ON sys_role_permission (tenant_id, role_id, permission_code);
CREATE INDEX idx_sys_role_permission_role
    ON sys_role_permission (tenant_id, role_id);


-- 角色数据范围：按 (角色, 功能点 action) 配置；action=NULL 行表示该角色默认范围
CREATE TABLE sys_role_data_scope
(
    id         BIGSERIAL    NOT NULL,
    tenant_id  BIGINT       NOT NULL DEFAULT 0,
    role_id    BIGINT       NOT NULL,
    action     VARCHAR(128),
    data_scope SMALLINT     NOT NULL,
    CONSTRAINT pk_sys_role_data_scope PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_role_data_scope IS '角色数据范围配置（混合表）。tenant_id=0 为运营角色；action=NULL 表示角色默认范围';
COMMENT ON COLUMN sys_role_data_scope.action IS '功能点 code（与 PermissionDef.code 对齐）；NULL = 该角色的默认数据范围';
COMMENT ON COLUMN sys_role_data_scope.data_scope IS
    '数据范围。租户域：1=全部 / 2=本部门及子孙 / 3=仅本人 / 4=仅本部门。'
    '运营域(tenant_id=0)：1=OPS_ALL / 3=OPS_OWNED';

-- 非 NULL action 每角色每功能点唯一
CREATE UNIQUE INDEX uk_sys_role_data_scope_role_action
    ON sys_role_data_scope (tenant_id, role_id, action)
    WHERE action IS NOT NULL;
-- NULL action（角色默认）每角色至多一条
CREATE UNIQUE INDEX uk_sys_role_data_scope_role_default
    ON sys_role_data_scope (tenant_id, role_id)
    WHERE action IS NULL;


CREATE TABLE sys_user_role
(
    id         BIGINT    NOT NULL,
    tenant_id  BIGINT    NOT NULL DEFAULT 0,
    user_id    BIGINT    NOT NULL,
    role_id    BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    CONSTRAINT pk_sys_user_role PRIMARY KEY (id)
);

COMMENT ON TABLE sys_user_role IS '用户 → 角色绑定（混合表）';

CREATE UNIQUE INDEX uk_sys_user_role_user_role
    ON sys_user_role (tenant_id, user_id, role_id);
CREATE INDEX idx_sys_user_role_user
    ON sys_user_role (tenant_id, user_id);
CREATE INDEX idx_sys_user_role_role
    ON sys_user_role (tenant_id, role_id);


CREATE TABLE sys_dept
(
    id         BIGINT       NOT NULL,
    tenant_id  BIGINT       NOT NULL DEFAULT 0,
    parent_id  BIGINT       NOT NULL DEFAULT 0,
    name       VARCHAR(128) NOT NULL,
    ancestors  TEXT         NOT NULL DEFAULT '/',
    sort_order INT          NOT NULL DEFAULT 0,
    status     SMALLINT     NOT NULL DEFAULT 1,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP,
    CONSTRAINT pk_sys_dept PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_dept IS '部门（混合表，parent_id + ancestors 物化路径）';
COMMENT ON COLUMN sys_dept.parent_id IS '父部门 ID；0 表示根部门';
COMMENT ON COLUMN sys_dept.ancestors IS '祖先路径，形如 /1/3/15/，根部门为 /';

CREATE UNIQUE INDEX uk_sys_dept_tenant_parent_name_alive
    ON sys_dept (tenant_id, parent_id, name)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_sys_dept_tenant_ancestors
    ON sys_dept (tenant_id, ancestors text_pattern_ops);
CREATE INDEX idx_sys_dept_tenant_parent
    ON sys_dept (tenant_id, parent_id);


CREATE TABLE sys_user_dept
(
    id         BIGINT    NOT NULL,
    tenant_id  BIGINT    NOT NULL DEFAULT 0,
    user_id    BIGINT    NOT NULL,
    dept_id    BIGINT    NOT NULL,
    is_main    SMALLINT  NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    CONSTRAINT pk_sys_user_dept PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_user_dept IS '用户 → 部门绑定（混合表）';
COMMENT ON COLUMN sys_user_dept.is_main IS '1=主部门（每用户至多 1 条） / 0=兼任';

CREATE UNIQUE INDEX uk_sys_user_dept_user_dept
    ON sys_user_dept (tenant_id, user_id, dept_id);
CREATE INDEX idx_sys_user_dept_user
    ON sys_user_dept (tenant_id, user_id);
CREATE INDEX idx_sys_user_dept_dept
    ON sys_user_dept (tenant_id, dept_id);


-- 部门负责人
CREATE TABLE sys_dept_leader
(
    id         BIGINT    NOT NULL,
    tenant_id  BIGINT    NOT NULL DEFAULT 0,
    dept_id    BIGINT    NOT NULL,
    user_id    BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    CONSTRAINT pk_sys_dept_leader PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_dept_leader IS '部门负责人（混合表）。一部门 0~N 个负责人，须为该部门成员';
COMMENT ON COLUMN sys_dept_leader.dept_id IS '部门 ID（sys_dept.id）';
COMMENT ON COLUMN sys_dept_leader.user_id IS '负责人 user_id（须在 sys_user_dept 中绑定该部门）';

CREATE UNIQUE INDEX uk_sys_dept_leader_dept_user
    ON sys_dept_leader (tenant_id, dept_id, user_id);
CREATE INDEX idx_sys_dept_leader_dept
    ON sys_dept_leader (tenant_id, dept_id);
CREATE INDEX idx_sys_dept_leader_user
    ON sys_dept_leader (tenant_id, user_id);


-- 第三方账号 OAuth 绑定
CREATE TABLE sys_user_oauth_binding
(
    id          BIGINT       NOT NULL,
    tenant_id   BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    provider    VARCHAR(32)  NOT NULL,
    external_id VARCHAR(128) NOT NULL,
    union_id    VARCHAR(128),
    nickname    VARCHAR(128),
    avatar_url  VARCHAR(512),
    bound_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  BIGINT,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  BIGINT,
    deleted_at  TIMESTAMP,
    CONSTRAINT pk_sys_user_oauth_binding PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_user_oauth_binding IS '第三方账号绑定（混合表，tenant_id 跟随被绑用户）';
COMMENT ON COLUMN sys_user_oauth_binding.id IS '雪花 ID';
COMMENT ON COLUMN sys_user_oauth_binding.provider IS '渠道：dingtalk（后续 wechat / wecom）';
COMMENT ON COLUMN sys_user_oauth_binding.external_id IS '渠道侧稳定标识：钉钉优先 unionId，回退 userId';
COMMENT ON COLUMN sys_user_oauth_binding.union_id IS '渠道 unionId 冗余留存，便于多应用聚合';
COMMENT ON COLUMN sys_user_oauth_binding.nickname IS '绑定时昵称快照，仅展示';
COMMENT ON COLUMN sys_user_oauth_binding.bound_at IS '绑定时刻';
COMMENT ON COLUMN sys_user_oauth_binding.deleted_at IS '软删除时间戳；解绑置非空，NULL 表示活跃绑定';

CREATE UNIQUE INDEX uk_oauth_provider_external_alive
    ON sys_user_oauth_binding (provider, external_id)
    WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uk_oauth_user_provider_alive
    ON sys_user_oauth_binding (user_id, provider)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_oauth_tenant_user
    ON sys_user_oauth_binding (tenant_id, user_id)
    WHERE deleted_at IS NULL;

-- ============================================================================
-- 4) 字典框架
-- ============================================================================
CREATE TABLE sys_dict_group
(
    id                BIGINT       NOT NULL,
    tenant_id         BIGINT       NOT NULL DEFAULT 0,
    code              VARCHAR(80)  NOT NULL,
    name              VARCHAR(128) NOT NULL,
    description       TEXT,
    source            SMALLINT     NOT NULL,
    module_code       VARCHAR(80),
    support_hierarchy BOOLEAN      NOT NULL DEFAULT FALSE,
    extra_schema      JSONB,
    status            SMALLINT     NOT NULL DEFAULT 1,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        BIGINT,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        BIGINT,
    deleted_at        TIMESTAMP,
    CONSTRAINT pk_sys_dict_group PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_dict_group IS '字典分组（混合表）。tenant_id=0 承载 SYSTEM/MODULE 共享分组；>0 为该租户的 CUSTOM 分组';
COMMENT ON COLUMN sys_dict_group.code IS '分组编码（租户内唯一，创建后不可改）';
COMMENT ON COLUMN sys_dict_group.source IS '1=SYSTEM 系统预置 / 2=MODULE 业务模块 / 3=CUSTOM 租户自定义';
COMMENT ON COLUMN sys_dict_group.module_code IS 'source=MODULE 时填业务模块 code，便于卸载/重启时定位';
COMMENT ON COLUMN sys_dict_group.support_hierarchy IS '是否支持层级（创建后不可改，最大深度 5 层）';
COMMENT ON COLUMN sys_dict_group.extra_schema IS '扩展字段 schema，JSON 数组 [{fieldCode,fieldName,dataType,required,description}]';
COMMENT ON COLUMN sys_dict_group.status IS '0=停用 / 1=启用';

CREATE UNIQUE INDEX uk_sys_dict_group_tenant_code_alive
    ON sys_dict_group (tenant_id, code)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_sys_dict_group_tenant_source
    ON sys_dict_group (tenant_id, source)
    WHERE deleted_at IS NULL;


CREATE TABLE sys_dict_item
(
    id          BIGINT       NOT NULL,
    tenant_id   BIGINT       NOT NULL DEFAULT 0,
    group_code  VARCHAR(80)  NOT NULL,
    code        VARCHAR(80)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    parent_id   BIGINT       NOT NULL DEFAULT 0,
    ancestors   TEXT         NOT NULL DEFAULT '/',
    sort_order  INT          NOT NULL DEFAULT 0,
    status      SMALLINT     NOT NULL DEFAULT 1,
    source      SMALLINT     NOT NULL,
    remark      TEXT,
    extra       JSONB,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  BIGINT,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  BIGINT,
    deleted_at  TIMESTAMP,
    CONSTRAINT pk_sys_dict_item PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_dict_item IS '字典项（混合表）。group_code 冗余便于查询，不带分组外键';
COMMENT ON COLUMN sys_dict_item.group_code IS '所属分组 code（与 sys_dict_group.code 对应；不带外键）';
COMMENT ON COLUMN sys_dict_item.ancestors IS '祖先路径，形如 /200001/200005/，顶级项为 /';
COMMENT ON COLUMN sys_dict_item.source IS '1=SYSTEM / 2=MODULE / 3=CUSTOM';
COMMENT ON COLUMN sys_dict_item.extra IS '扩展字段值，JSON 对象';

CREATE UNIQUE INDEX uk_sys_dict_item_tenant_group_code_alive
    ON sys_dict_item (tenant_id, group_code, code)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_sys_dict_item_tenant_group
    ON sys_dict_item (tenant_id, group_code)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_sys_dict_item_tenant_parent
    ON sys_dict_item (tenant_id, parent_id)
    WHERE deleted_at IS NULL;


-- ============================================================================
-- 5) 自定义字段框架
-- ============================================================================
CREATE TABLE sys_custom_field_def
(
    id              BIGINT       NOT NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 0,
    object_code     VARCHAR(80)  NOT NULL,
    field_code      VARCHAR(80)  NOT NULL,
    field_name      VARCHAR(255) NOT NULL,
    field_type      VARCHAR(20)  NOT NULL,
    is_required     BOOLEAN      NOT NULL DEFAULT FALSE,
    is_indexed      BOOLEAN      NOT NULL DEFAULT FALSE,
    default_value   TEXT,
    ref_dict_code   VARCHAR(80),
    ref_object_code VARCHAR(80),
    display_order   INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    deleted_at      TIMESTAMP,
    CONSTRAINT pk_sys_custom_field_def PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_custom_field_def IS '自定义字段定义（混合表）。租户级，按 (tenant_id, object_code, field_code) 唯一';
COMMENT ON COLUMN sys_custom_field_def.object_code IS '业务对象 code，与 CustomFieldObjectDef.code 对应（如 crm.customer）';
COMMENT ON COLUMN sys_custom_field_def.field_code IS '字段 code，JSON key（创建后不可改，租户内同 object 唯一）';
COMMENT ON COLUMN sys_custom_field_def.field_type IS 'text / long_text / number / date / bool / enum / ref；改类型禁止';
COMMENT ON COLUMN sys_custom_field_def.is_indexed IS '是否启用 PG 表达式索引（is_indexed=true 时占 index_used 配额）';
COMMENT ON COLUMN sys_custom_field_def.ref_dict_code IS 'field_type=enum 时必填，对应 sys_dict_group.code';
COMMENT ON COLUMN sys_custom_field_def.ref_object_code IS 'field_type=ref 时必填，对应另一 CustomFieldObjectDef.code';

CREATE UNIQUE INDEX uk_sys_custom_field_def_tenant_object_code_alive
    ON sys_custom_field_def (tenant_id, object_code, field_code)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_sys_custom_field_def_tenant_object
    ON sys_custom_field_def (tenant_id, object_code)
    WHERE deleted_at IS NULL;


CREATE TABLE sys_custom_field_quota
(
    tenant_id    BIGINT     NOT NULL,
    field_quota  INTEGER    NOT NULL DEFAULT 20,
    field_used   INTEGER    NOT NULL DEFAULT 0,
    index_quota  INTEGER    NOT NULL DEFAULT 5,
    index_used   INTEGER    NOT NULL DEFAULT 0,
    updated_at   TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sys_custom_field_quota PRIMARY KEY (tenant_id),
    CONSTRAINT ck_sys_custom_field_quota_field CHECK (field_used >= 0 AND field_used <= field_quota),
    CONSTRAINT ck_sys_custom_field_quota_index CHECK (index_used >= 0 AND index_used <= index_quota)
);

COMMENT ON TABLE  sys_custom_field_quota IS '租户字段 / 索引额度（基础 20/5，运营加购可叠加）';
COMMENT ON COLUMN sys_custom_field_quota.field_quota IS '字段总额度（基础 20 + 加购）';
COMMENT ON COLUMN sys_custom_field_quota.index_quota IS '索引总额度（基础 5 + 加购）';


-- ============================================================================
-- 6) 操作日志 / 登录日志（月度分区）
-- ============================================================================
CREATE TABLE sys_operation_log
(
    id               BIGINT       NOT NULL,
    tenant_id        BIGINT       NOT NULL DEFAULT 0,
    target_tenant_id BIGINT,
    request_id       VARCHAR(64)  NOT NULL,
    operator_id      BIGINT       NOT NULL,
    operator_name    VARCHAR(255),
    operator_dept_id BIGINT,
    module           VARCHAR(80),
    action           VARCHAR(40),
    object_type      VARCHAR(80),
    object_id        VARCHAR(80),
    object_desc      VARCHAR(255),
    -- 多主对象引用；字段级变更明细记在 ③ 数据变更日志，不在本表
    object_refs      JSONB,
    method           VARCHAR(10),
    path             VARCHAR(500),
    status_code      INTEGER,
    duration_ms      INTEGER,
    error_message    TEXT,
    ip               VARCHAR(45),
    user_agent       VARCHAR(500),
    created_at       TIMESTAMP    NOT NULL,
    CONSTRAINT pk_sys_operation_log PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

COMMENT ON TABLE  sys_operation_log IS '操作日志（混合表 + 月度分区）。写入后不更新';
COMMENT ON COLUMN sys_operation_log.tenant_id IS '操作产生方租户；0=运营';
COMMENT ON COLUMN sys_operation_log.target_tenant_id IS '运营对租户操作时填租户 ID；否则 NULL';
COMMENT ON COLUMN sys_operation_log.operator_dept_id IS '操作时刻主部门快照；NULL 表示无主部门';
COMMENT ON COLUMN sys_operation_log.object_refs IS '多主对象引用 [{type,id,name}]；按对象 id 追溯用';

CREATE INDEX idx_oplog_tenant_time
    ON sys_operation_log (tenant_id, created_at DESC);
CREATE INDEX idx_oplog_operator
    ON sys_operation_log (tenant_id, operator_id, created_at DESC);
CREATE INDEX idx_oplog_request
    ON sys_operation_log (request_id);
CREATE INDEX idx_oplog_target
    ON sys_operation_log (target_tenant_id, created_at DESC)
    WHERE target_tenant_id IS NOT NULL;
CREATE INDEX idx_oplog_dept
    ON sys_operation_log (tenant_id, operator_dept_id, created_at DESC)
    WHERE operator_dept_id IS NOT NULL;
CREATE INDEX idx_oplog_object_refs
    ON sys_operation_log USING GIN (object_refs);

CREATE TABLE sys_operation_log_p202605 PARTITION OF sys_operation_log
    FOR VALUES FROM ('2026-05-01 00:00:00') TO ('2026-06-01 00:00:00');
CREATE TABLE sys_operation_log_p202606 PARTITION OF sys_operation_log
    FOR VALUES FROM ('2026-06-01 00:00:00') TO ('2026-07-01 00:00:00');
CREATE TABLE sys_operation_log_p202607 PARTITION OF sys_operation_log
    FOR VALUES FROM ('2026-07-01 00:00:00') TO ('2026-08-01 00:00:00');
CREATE TABLE sys_operation_log_p202608 PARTITION OF sys_operation_log
    FOR VALUES FROM ('2026-08-01 00:00:00') TO ('2026-09-01 00:00:00');
CREATE TABLE sys_operation_log_p202609 PARTITION OF sys_operation_log
    FOR VALUES FROM ('2026-09-01 00:00:00') TO ('2026-10-01 00:00:00');
CREATE TABLE sys_operation_log_default PARTITION OF sys_operation_log DEFAULT;


CREATE TABLE sys_login_log
(
    id               BIGINT       NOT NULL,
    tenant_id        BIGINT       NOT NULL DEFAULT 0,
    request_id       VARCHAR(64)  NOT NULL,
    operator_id      BIGINT,
    operator_name    VARCHAR(255),
    operator_dept_id BIGINT,
    account          VARCHAR(255) NOT NULL,
    login_method     VARCHAR(40)  NOT NULL,
    action           VARCHAR(20)  NOT NULL,
    result           VARCHAR(10)  NOT NULL,
    fail_reason      VARCHAR(40),
    ip               VARCHAR(45),
    user_agent       VARCHAR(500),
    created_at       TIMESTAMP    NOT NULL,
    CONSTRAINT pk_sys_login_log PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

COMMENT ON TABLE  sys_login_log IS '登录日志（混合表 + 月度分区）';
COMMENT ON COLUMN sys_login_log.operator_id IS '用户 ID；账号不存在时 NULL';
COMMENT ON COLUMN sys_login_log.account IS '用户输入账号原文（用于排查）';
COMMENT ON COLUMN sys_login_log.login_method IS 'password / sms / wechat / wecom / dingtalk';
COMMENT ON COLUMN sys_login_log.action IS 'login / logout / session_expired';
COMMENT ON COLUMN sys_login_log.result IS 'success / fail';
COMMENT ON COLUMN sys_login_log.fail_reason IS 'bad_credentials / disabled / locked / oauth_failed / other';

CREATE INDEX idx_loginlog_tenant_time
    ON sys_login_log (tenant_id, created_at DESC);
CREATE INDEX idx_loginlog_operator
    ON sys_login_log (tenant_id, operator_id, created_at DESC)
    WHERE operator_id IS NOT NULL;
CREATE INDEX idx_loginlog_account
    ON sys_login_log (tenant_id, account, created_at DESC);
CREATE INDEX idx_loginlog_request
    ON sys_login_log (request_id);
CREATE INDEX idx_loginlog_dept
    ON sys_login_log (tenant_id, operator_dept_id, created_at DESC)
    WHERE operator_dept_id IS NOT NULL;

CREATE TABLE sys_login_log_p202605 PARTITION OF sys_login_log
    FOR VALUES FROM ('2026-05-01 00:00:00') TO ('2026-06-01 00:00:00');
CREATE TABLE sys_login_log_p202606 PARTITION OF sys_login_log
    FOR VALUES FROM ('2026-06-01 00:00:00') TO ('2026-07-01 00:00:00');
CREATE TABLE sys_login_log_p202607 PARTITION OF sys_login_log
    FOR VALUES FROM ('2026-07-01 00:00:00') TO ('2026-08-01 00:00:00');
CREATE TABLE sys_login_log_p202608 PARTITION OF sys_login_log
    FOR VALUES FROM ('2026-08-01 00:00:00') TO ('2026-09-01 00:00:00');
CREATE TABLE sys_login_log_p202609 PARTITION OF sys_login_log
    FOR VALUES FROM ('2026-09-01 00:00:00') TO ('2026-10-01 00:00:00');
CREATE TABLE sys_login_log_default PARTITION OF sys_login_log DEFAULT;


-- ============================================================================
-- 7) 任务中心
-- ============================================================================
CREATE TABLE sys_task
(
    id                BIGINT       NOT NULL PRIMARY KEY,
    tenant_id         BIGINT       NOT NULL DEFAULT 0,
    task_type         VARCHAR(40)  NOT NULL,
    task_name         VARCHAR(255),
    handler_code      VARCHAR(80)  NOT NULL,
    task_params       JSONB,
    status            VARCHAR(30)  NOT NULL,
    progress          INTEGER      NOT NULL DEFAULT 0,
    total_count       INTEGER,
    processed_count   INTEGER,
    success_count     INTEGER,
    failed_count      INTEGER,
    result_data       JSONB,
    error_message     TEXT,
    creator_id        BIGINT       NOT NULL,
    created_at        TIMESTAMP    NOT NULL,
    started_at        TIMESTAMP,
    finished_at       TIMESTAMP,
    expires_at        TIMESTAMP    NOT NULL
);

COMMENT ON TABLE  sys_task IS '统一异步任务表（混合表）';
COMMENT ON COLUMN sys_task.tenant_id IS '提交方租户；0=运营';
COMMENT ON COLUMN sys_task.task_type IS 'IMPORT/EXPORT/BATCH/OTHER';
COMMENT ON COLUMN sys_task.handler_code IS '业务模块表前缀.task.* 或 sys.task.*';
COMMENT ON COLUMN sys_task.status IS 'PENDING/RUNNING/COMPLETED/FAILED/CANCELLING/CANCELLED';
COMMENT ON COLUMN sys_task.progress IS '0-100';
COMMENT ON COLUMN sys_task.expires_at IS '过期时间；默认提交后 30 天，由 Quartz 定时任务物理清理';

CREATE INDEX idx_task_tenant_creator
    ON sys_task (tenant_id, creator_id, created_at DESC);
CREATE INDEX idx_task_tenant_status
    ON sys_task (tenant_id, status, created_at DESC);
CREATE INDEX idx_task_expires
    ON sys_task (expires_at);

-- ============================================================================
-- 8) 文件存储（含文件夹树 / 分享）
-- ============================================================================
CREATE TABLE sys_file
(
    id                   BIGINT       NOT NULL PRIMARY KEY,
    tenant_id            BIGINT       NOT NULL DEFAULT 0,
    file_id              VARCHAR(64)  NOT NULL,
    file_name            VARCHAR(255),
    mime_type            VARCHAR(100),
    size_bytes           BIGINT,
    visibility           VARCHAR(20)  NOT NULL,
    owner_module         VARCHAR(80),
    owner_ref_type       VARCHAR(80),
    owner_ref_id         BIGINT,
    folder_path          VARCHAR(500),
    -- 文件中心维度
    space                VARCHAR(20),
    folder_id            BIGINT,
    backend_type         VARCHAR(40)  NOT NULL,
    backend_config_id    BIGINT,
    backend_object_key   VARCHAR(500) NOT NULL,
    upload_state         VARCHAR(20)  NOT NULL,
    uploaded_by          BIGINT,
    uploaded_at          TIMESTAMP,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by           BIGINT,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by           BIGINT,
    deleted_at           TIMESTAMP
);

COMMENT ON TABLE  sys_file IS '文件元数据（混合表）。本体存配置的后端，元数据永远在 PG';
COMMENT ON COLUMN sys_file.tenant_id IS '租户 ID；0=平台文件';
COMMENT ON COLUMN sys_file.file_id IS '全局唯一对外 ID（Base62 雪花）';
COMMENT ON COLUMN sys_file.visibility IS 'file-center 文件中心可见 / business-only 仅业务模块';
COMMENT ON COLUMN sys_file.space IS '文件中心空间 mine/shared；business-only 文件为 NULL';
COMMENT ON COLUMN sys_file.folder_id IS '所在文件夹 id（引用 sys_file_folder.id）；NULL=该 space 根目录';
COMMENT ON COLUMN sys_file.backend_type IS 'local / aliyun_oss / 其他';
COMMENT ON COLUMN sys_file.backend_config_id IS '引用 sys_storage_config.id；NULL=平台默认后端';
COMMENT ON COLUMN sys_file.upload_state IS 'uploading / active / failed';
COMMENT ON COLUMN sys_file.deleted_at IS '软删时间戳；超过 30 天由 Quartz 定时任务物理删除';

CREATE UNIQUE INDEX uk_sys_file_file_id_alive
    ON sys_file (file_id)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_file_tenant
    ON sys_file (tenant_id, deleted_at)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_file_owner
    ON sys_file (tenant_id, owner_module, owner_ref_type, owner_ref_id);
CREATE INDEX idx_file_folder
    ON sys_file (tenant_id, folder_path)
    WHERE visibility = 'file-center';
CREATE INDEX idx_file_center_folder
    ON sys_file (tenant_id, space, folder_id)
    WHERE visibility = 'file-center' AND deleted_at IS NULL;


CREATE TABLE sys_storage_config
(
    id                  BIGINT       NOT NULL PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL DEFAULT 0,
    backend_type        VARCHAR(40)  NOT NULL,
    endpoint            VARCHAR(255),
    bucket              VARCHAR(255),
    access_key_id       VARCHAR(255),
    access_key_secret   VARCHAR(500),
    path_prefix         VARCHAR(255),
    status              VARCHAR(20)  NOT NULL DEFAULT 'active',
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT
);

COMMENT ON TABLE  sys_storage_config IS '存储后端配置（混合表）。每租户至多一个 active 配置，历史 archived 保留供旧文件下载';
COMMENT ON COLUMN sys_storage_config.tenant_id IS '租户 ID；0=平台默认（DDL 兜底，框架运行时也支持 application.yml 直接配置不入表）';
COMMENT ON COLUMN sys_storage_config.backend_type IS 'local / aliyun_oss / tencent_cos(P1) / ...';
COMMENT ON COLUMN sys_storage_config.access_key_secret IS 'AES-256-GCM 加密；Base64(IV[12] || ciphertext || tag[16])';
COMMENT ON COLUMN sys_storage_config.status IS 'active / archived（租户切换后旧配置标 archived，历史文件仍可下载）';

CREATE UNIQUE INDEX uk_sys_storage_config_active
    ON sys_storage_config (tenant_id)
    WHERE status = 'active';
CREATE INDEX idx_storage_tenant
    ON sys_storage_config (tenant_id, status);


-- 文件中心文件夹树
CREATE TABLE sys_file_folder
(
    id          BIGINT       NOT NULL PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL DEFAULT 0,
    space       VARCHAR(20)  NOT NULL,
    parent_id   BIGINT,
    name        VARCHAR(255) NOT NULL,
    depth       SMALLINT     NOT NULL DEFAULT 1,
    owner_id    BIGINT,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  BIGINT,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  BIGINT,
    deleted_at  TIMESTAMP
);

COMMENT ON TABLE  sys_file_folder IS '文件中心文件夹树（混合表）。space=mine 按 owner_id 隔离，shared 全租户可见';
COMMENT ON COLUMN sys_file_folder.space IS 'mine=我的文件 / shared=共享文件';
COMMENT ON COLUMN sys_file_folder.parent_id IS '父文件夹 id；NULL=该 space 根目录';
COMMENT ON COLUMN sys_file_folder.depth IS '层级，根=1，最深 5（service 校验）';
COMMENT ON COLUMN sys_file_folder.owner_id IS 'mine 空间为创建者用户 id；shared 为 NULL';
COMMENT ON COLUMN sys_file_folder.deleted_at IS '软删时间戳；超过 30 天由清理 Job 物理删除';

CREATE UNIQUE INDEX uk_folder_sibling_name_alive
    ON sys_file_folder (tenant_id, space, COALESCE(owner_id, 0), COALESCE(parent_id, 0), name)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_folder_parent
    ON sys_file_folder (tenant_id, space, parent_id)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_folder_recycle
    ON sys_file_folder (tenant_id, deleted_at)
    WHERE deleted_at IS NOT NULL;


-- 文件分享链接
CREATE TABLE sys_file_share
(
    id              BIGINT       NOT NULL PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 0,
    share_token     VARCHAR(64)  NOT NULL,
    file_id         VARCHAR(64)  NOT NULL,
    expires_at      TIMESTAMP,
    password_hash   VARCHAR(60),
    max_downloads   INTEGER,
    download_count  INTEGER      NOT NULL DEFAULT 0,
    status          VARCHAR(20)  NOT NULL DEFAULT 'active',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    deleted_at      TIMESTAMP
);

COMMENT ON TABLE  sys_file_share IS '文件分享链接 v2（混合表）';
COMMENT ON COLUMN sys_file_share.tenant_id IS '租户 ID；分享 P0 仅限租户内访问';
COMMENT ON COLUMN sys_file_share.share_token IS 'Base62 雪花，对外短链 ID';
COMMENT ON COLUMN sys_file_share.file_id IS '引用 sys_file.file_id';
COMMENT ON COLUMN sys_file_share.expires_at IS '过期时间；NULL = 永久（产品 P0 档位 7d/30d/永久）';
COMMENT ON COLUMN sys_file_share.password_hash IS 'BCrypt $2a$10$...; NULL = 无密码';
COMMENT ON COLUMN sys_file_share.max_downloads IS 'NULL = 不限次数';
COMMENT ON COLUMN sys_file_share.download_count IS '已下载次数（原子自增）';
COMMENT ON COLUMN sys_file_share.status IS 'active / revoked';
COMMENT ON COLUMN sys_file_share.deleted_at IS '软删时间戳；revoke 同步软删';

CREATE UNIQUE INDEX uk_sys_file_share_token
    ON sys_file_share (share_token)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_sys_file_share_file
    ON sys_file_share (tenant_id, file_id)
    WHERE deleted_at IS NULL;


-- ============================================================================
-- 9) 通知与多通道（含模板偏好 / 用户偏好）
-- ============================================================================
CREATE TABLE sys_notification_template
(
    id                    BIGINT       NOT NULL PRIMARY KEY,
    tenant_id             BIGINT       NOT NULL DEFAULT 0,
    template_code         VARCHAR(80)  NOT NULL,
    module_code           VARCHAR(80),
    name                  VARCHAR(255) NOT NULL,
    description           TEXT,
    available_vars        JSONB,
    default_channels      JSONB        NOT NULL,
    contents              JSONB        NOT NULL,
    notification_category VARCHAR(40),
    priority              VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',
    status                VARCHAR(20)  NOT NULL DEFAULT 'active',
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by            BIGINT,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by            BIGINT,
    deleted_at            TIMESTAMP
);

COMMENT ON TABLE  sys_notification_template IS '通知模板。文案恒取 tenant_id=0 的平台默认行；租户定制走 sys_notification_template_pref（仅启停/通道/优先级），tenant_id>0 的行不参与模板解析';
COMMENT ON COLUMN sys_notification_template.tenant_id IS '恒为 0（平台默认模板）；tenant_id>0 的行不参与模板解析，租户级定制见 sys_notification_template_pref';
COMMENT ON COLUMN sys_notification_template.template_code IS '{module}.{template_name}，全局唯一';
COMMENT ON COLUMN sys_notification_template.available_vars IS '可用变量声明 [{name, sampleValue, required}]';
COMMENT ON COLUMN sys_notification_template.default_channels IS '默认通道列表 ["STATION","EMAIL"]';
COMMENT ON COLUMN sys_notification_template.contents IS '各通道文案 {STATION:{title,body}, EMAIL:{title,body}, ...}';
COMMENT ON COLUMN sys_notification_template.notification_category IS '通知类别（NotificationCategory 枚举名）；非空时参与用户通知偏好过滤，空表示不过滤';
COMMENT ON COLUMN sys_notification_template.priority IS 'NORMAL / IMPORTANT';
COMMENT ON COLUMN sys_notification_template.status IS 'active / inactive';

CREATE UNIQUE INDEX uk_sys_notify_tpl_tenant_code_alive
    ON sys_notification_template (tenant_id, template_code)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_sys_notify_tpl_module
    ON sys_notification_template (tenant_id, module_code)
    WHERE deleted_at IS NULL;


CREATE TABLE sys_message_task
(
    id                BIGINT       NOT NULL PRIMARY KEY,
    tenant_id         BIGINT       NOT NULL DEFAULT 0,
    task_id           VARCHAR(64)  NOT NULL,
    template_code     VARCHAR(80)  NOT NULL,
    recipient_id      BIGINT       NOT NULL,
    channel           VARCHAR(20)  NOT NULL,
    priority          VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',
    rendered_title    VARCHAR(500),
    rendered_body     TEXT,
    related_type      VARCHAR(80),
    related_id        VARCHAR(80),
    link              VARCHAR(500),
    status            VARCHAR(20)  NOT NULL,
    retry_count       INTEGER      NOT NULL DEFAULT 0,
    error_reason      TEXT,
    sent_at           TIMESTAMP,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  sys_message_task IS '消息任务表（混合表）。一条 sendMessage 拆为 收件人 × 通道 N 行';
COMMENT ON COLUMN sys_message_task.task_id IS '业务方拿到的对外 ID（同一次 send 共享）';
COMMENT ON COLUMN sys_message_task.channel IS 'STATION / EMAIL / SMS / WECHAT / WECOM / DINGTALK';
COMMENT ON COLUMN sys_message_task.status IS 'PENDING / SENT / FAILED / SKIPPED';

CREATE INDEX idx_sys_msgtask_tenant_status
    ON sys_message_task (tenant_id, status, created_at DESC);
CREATE INDEX idx_sys_msgtask_task_id
    ON sys_message_task (task_id);
CREATE INDEX idx_sys_msgtask_recipient
    ON sys_message_task (tenant_id, recipient_id, created_at DESC);


CREATE TABLE sys_station_message
(
    id                BIGINT       NOT NULL PRIMARY KEY,
    tenant_id         BIGINT       NOT NULL,
    recipient_id      BIGINT       NOT NULL,
    category          VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
    source_module     VARCHAR(80),
    template_code     VARCHAR(80),
    -- 关联当次发送的 sys_message_task
    task_id           VARCHAR(64),
    title             VARCHAR(500) NOT NULL,
    body              TEXT,
    link              VARCHAR(500),
    related_type      VARCHAR(80),
    related_id        VARCHAR(80),
    priority          VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',
    read_at           TIMESTAMP,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  sys_station_message IS '站内信本体（混合表）。按收件人独立行';
COMMENT ON COLUMN sys_station_message.category IS 'SYSTEM / BUSINESS / TODO';
COMMENT ON COLUMN sys_station_message.task_id IS '对应 sys_message_task.task_id；投递明细按 task 关联各通道结果';
COMMENT ON COLUMN sys_station_message.read_at IS 'NULL = 未读；非空 = 用户首次打开时间';

CREATE INDEX idx_sys_station_recipient
    ON sys_station_message (tenant_id, recipient_id, read_at, created_at DESC);
CREATE INDEX idx_sys_station_recipient_unread
    ON sys_station_message (tenant_id, recipient_id, created_at DESC)
    WHERE read_at IS NULL;
CREATE INDEX idx_sys_station_task
    ON sys_station_message (task_id)
    WHERE task_id IS NOT NULL;
CREATE INDEX idx_sys_station_inbox
    ON sys_station_message (tenant_id, recipient_id, category, read_at, created_at DESC);


-- 用户通知偏好
CREATE TABLE sys_user_notification_preference
(
    id          BIGINT      NOT NULL PRIMARY KEY,
    tenant_id   BIGINT      NOT NULL DEFAULT 0,
    user_id     BIGINT      NOT NULL,
    category    VARCHAR(40) NOT NULL,
    channel     VARCHAR(20) NOT NULL,
    enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  sys_user_notification_preference IS '用户通知偏好（混合表）。按 用户×类别×通道 一行；缺行=默认开启';
COMMENT ON COLUMN sys_user_notification_preference.category IS 'NotificationCategory 枚举名';
COMMENT ON COLUMN sys_user_notification_preference.channel IS 'STATION / EMAIL / SMS / WECHAT / WECOM / DINGTALK；STATION 强制开';
COMMENT ON COLUMN sys_user_notification_preference.enabled IS 'true=接收 / false=不接收该类该通道';

CREATE UNIQUE INDEX uk_sys_user_notify_pref
    ON sys_user_notification_preference (tenant_id, user_id, category, channel);


-- 租户通知模板偏好
CREATE TABLE sys_notification_template_pref
(
    id              BIGINT       NOT NULL PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL,
    template_code   VARCHAR(80)  NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    channels        JSONB,
    priority        VARCHAR(20),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    deleted_at      TIMESTAMP
);

COMMENT ON TABLE  sys_notification_template_pref IS '租户通知模板偏好（轻量定制；仅启停/渠道/优先级，文案恒用 tenant_id=0 默认）';
COMMENT ON COLUMN sys_notification_template_pref.template_code IS '对应 sys_notification_template(tenant_id=0).template_code';
COMMENT ON COLUMN sys_notification_template_pref.enabled IS 'false=该模板对本租户完全不发（含站内信兜底）';
COMMENT ON COLUMN sys_notification_template_pref.channels IS '覆盖通道列表 ["STATION","EMAIL"]；NULL=继承默认。落库强制含 STATION';
COMMENT ON COLUMN sys_notification_template_pref.priority IS '覆盖优先级 NORMAL / IMPORTANT；NULL=继承默认';

CREATE UNIQUE INDEX uk_sys_notify_tpl_pref_tenant_code_alive
    ON sys_notification_template_pref (tenant_id, template_code)
    WHERE deleted_at IS NULL;

-- ============================================================================
-- 10) 开放 API
-- ============================================================================
CREATE TABLE sys_api_key
(
    id                BIGINT       NOT NULL PRIMARY KEY,
    tenant_id         BIGINT       NOT NULL DEFAULT 0,
    name              VARCHAR(255),
    api_key           VARCHAR(64)  NOT NULL,
    secret_encrypted  VARCHAR(512) NOT NULL,
    ip_whitelist      JSONB,
    require_signature BOOLEAN      NOT NULL DEFAULT FALSE,
    -- 凭证级限流
    rate_limit_qps    INTEGER,
    expires_at        TIMESTAMP,
    status            VARCHAR(20)  NOT NULL DEFAULT 'active',
    creator_id        BIGINT,
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT now(),
    last_used_at      TIMESTAMP
);

COMMENT ON TABLE  sys_api_key IS 'API 凭证（混合表，非分区）';
COMMENT ON COLUMN sys_api_key.tenant_id IS '凭证所属租户；0=运营';
COMMENT ON COLUMN sys_api_key.api_key IS 'ak_xxxxx 32 字符随机串';
COMMENT ON COLUMN sys_api_key.secret_encrypted IS 'AES-256-GCM 加密后的 secret（CredentialCipher 多版本密钥）';
COMMENT ON COLUMN sys_api_key.ip_whitelist IS '白名单数组 ["1.2.3.4", "10.0.0.0/8"]，NULL 表示不限';
COMMENT ON COLUMN sys_api_key.require_signature IS '是否强制 HMAC 签名';
COMMENT ON COLUMN sys_api_key.rate_limit_qps IS '凭证级限流配额（每限流窗口允许的请求数）；NULL=不启用凭证级、仅受租户级限流';
COMMENT ON COLUMN sys_api_key.status IS 'active / disabled / expired';

CREATE UNIQUE INDEX uk_apikey_lookup
    ON sys_api_key (api_key)
    WHERE status = 'active';
CREATE INDEX idx_apikey_tenant
    ON sys_api_key (tenant_id, status);


CREATE TABLE sys_api_call_log
(
    id            BIGINT       NOT NULL,
    tenant_id     BIGINT       NOT NULL DEFAULT 0,
    api_key_id    BIGINT,
    api_key_name  VARCHAR(255),
    request_id    VARCHAR(64)  NOT NULL,
    method        VARCHAR(10),
    path          VARCHAR(500),
    status_code   INTEGER,
    duration_ms   INTEGER,
    request_size  INTEGER,
    response_size INTEGER,
    error_message TEXT,
    ip            VARCHAR(45),
    user_agent    VARCHAR(500),
    -- 业务模块归类
    module_code   VARCHAR(64),
    created_at    TIMESTAMP    NOT NULL,
    CONSTRAINT pk_sys_api_call_log PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

COMMENT ON TABLE  sys_api_call_log IS 'API 调用日志（混合表 + 月度分区）。写入后不更新';
COMMENT ON COLUMN sys_api_call_log.tenant_id IS '调用所属租户；0=运营 / 匿名';
COMMENT ON COLUMN sys_api_call_log.api_key_id IS '凭证 ID；匿名调用时 NULL';
COMMENT ON COLUMN sys_api_call_log.api_key_name IS '凭证名快照（凭证可能撤销）';
COMMENT ON COLUMN sys_api_call_log.module_code IS '业务模块编码（ModuleApiRouteRegistry 解析；命中不到为 NULL）';

CREATE INDEX idx_apilog_tenant_time
    ON sys_api_call_log (tenant_id, created_at DESC);
CREATE INDEX idx_apilog_apikey
    ON sys_api_call_log (api_key_id, created_at DESC)
    WHERE api_key_id IS NOT NULL;
CREATE INDEX idx_apilog_request
    ON sys_api_call_log (request_id);
CREATE INDEX idx_apilog_module
    ON sys_api_call_log (tenant_id, module_code, created_at DESC)
    WHERE module_code IS NOT NULL;

CREATE TABLE sys_api_call_log_p202605 PARTITION OF sys_api_call_log
    FOR VALUES FROM ('2026-05-01 00:00:00') TO ('2026-06-01 00:00:00');
CREATE TABLE sys_api_call_log_p202606 PARTITION OF sys_api_call_log
    FOR VALUES FROM ('2026-06-01 00:00:00') TO ('2026-07-01 00:00:00');
CREATE TABLE sys_api_call_log_p202607 PARTITION OF sys_api_call_log
    FOR VALUES FROM ('2026-07-01 00:00:00') TO ('2026-08-01 00:00:00');
CREATE TABLE sys_api_call_log_p202608 PARTITION OF sys_api_call_log
    FOR VALUES FROM ('2026-08-01 00:00:00') TO ('2026-09-01 00:00:00');
CREATE TABLE sys_api_call_log_p202609 PARTITION OF sys_api_call_log
    FOR VALUES FROM ('2026-09-01 00:00:00') TO ('2026-10-01 00:00:00');
CREATE TABLE sys_api_call_log_default PARTITION OF sys_api_call_log DEFAULT;


-- ============================================================================
-- 11) Webhook（含月度分区日志）
-- ============================================================================
CREATE TABLE sys_webhook
(
    id                        BIGINT       NOT NULL PRIMARY KEY,
    tenant_id                 BIGINT       NOT NULL DEFAULT 0,
    name                      VARCHAR(255),
    target_url                VARCHAR(500) NOT NULL,
    signing_secret_encrypted  VARCHAR(512) NOT NULL,
    subscribed_events         JSONB        NOT NULL DEFAULT '[]'::JSONB,
    retry_policy              JSONB,
    consecutive_failures      INTEGER      NOT NULL DEFAULT 0,
    status                    VARCHAR(20)  NOT NULL DEFAULT 'active',
    creator_id                BIGINT,
    created_at                TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at                TIMESTAMP    NOT NULL DEFAULT now()
);

COMMENT ON TABLE  sys_webhook IS 'Webhook 推送配置（混合表，非分区）';
COMMENT ON COLUMN sys_webhook.tenant_id IS '订阅方租户；0=运营';
COMMENT ON COLUMN sys_webhook.target_url IS '订阅方目标 URL';
COMMENT ON COLUMN sys_webhook.signing_secret_encrypted IS 'AES-256-GCM 加密后的 signing secret（CredentialCipher 多版本密钥）';
COMMENT ON COLUMN sys_webhook.subscribed_events IS '订阅事件类型数组 ["tenant.opened","crm.customer_created"]';
COMMENT ON COLUMN sys_webhook.retry_policy IS 'NULL 表示用全局默认；非空时格式 {max:3, backoff:[5,30,300]}';
COMMENT ON COLUMN sys_webhook.consecutive_failures IS '连续失败次数（v2 用于自动 disable，v1 保留不读）';
COMMENT ON COLUMN sys_webhook.status IS 'active / disabled / deleted';

CREATE INDEX idx_webhook_tenant_status
    ON sys_webhook (tenant_id, status);
CREATE INDEX idx_webhook_subscribed_events
    ON sys_webhook USING GIN (subscribed_events);


CREATE TABLE sys_webhook_log
(
    id            BIGINT       NOT NULL,
    tenant_id     BIGINT       NOT NULL DEFAULT 0,
    webhook_id    BIGINT       NOT NULL,
    event_type    VARCHAR(80),
    event_id      VARCHAR(64),
    request_body  TEXT,
    request_id    VARCHAR(64),
    http_status   INTEGER,
    response_body TEXT,
    duration_ms   INTEGER,
    error_message TEXT,
    retry_count   INTEGER      NOT NULL DEFAULT 0,
    final_status  VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    CONSTRAINT pk_sys_webhook_log PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

COMMENT ON TABLE  sys_webhook_log IS 'Webhook 推送日志（混合表 + 月度分区）。写入后不更新';
COMMENT ON COLUMN sys_webhook_log.event_id IS '上游 DomainEvent eventId（用于幂等关联）';
COMMENT ON COLUMN sys_webhook_log.final_status IS 'success / failed / abandoned（达到 retry 上限）';

CREATE INDEX idx_webhooklog_tenant_time
    ON sys_webhook_log (tenant_id, created_at DESC);
CREATE INDEX idx_webhooklog_webhook
    ON sys_webhook_log (webhook_id, created_at DESC);

CREATE TABLE sys_webhook_log_p202605 PARTITION OF sys_webhook_log
    FOR VALUES FROM ('2026-05-01 00:00:00') TO ('2026-06-01 00:00:00');
CREATE TABLE sys_webhook_log_p202606 PARTITION OF sys_webhook_log
    FOR VALUES FROM ('2026-06-01 00:00:00') TO ('2026-07-01 00:00:00');
CREATE TABLE sys_webhook_log_p202607 PARTITION OF sys_webhook_log
    FOR VALUES FROM ('2026-07-01 00:00:00') TO ('2026-08-01 00:00:00');
CREATE TABLE sys_webhook_log_p202608 PARTITION OF sys_webhook_log
    FOR VALUES FROM ('2026-08-01 00:00:00') TO ('2026-09-01 00:00:00');
CREATE TABLE sys_webhook_log_p202609 PARTITION OF sys_webhook_log
    FOR VALUES FROM ('2026-09-01 00:00:00') TO ('2026-10-01 00:00:00');
CREATE TABLE sys_webhook_log_default PARTITION OF sys_webhook_log DEFAULT;


-- ============================================================================
-- 12) 套餐与租户授权（租户已选套餐记在 sys_tenant.plans）
-- ============================================================================
CREATE TABLE sys_package
(
    id                BIGINT       NOT NULL,
    code              VARCHAR(64)  NOT NULL,
    name              VARCHAR(128) NOT NULL,
    description       TEXT,
    is_system         BOOLEAN      NOT NULL DEFAULT FALSE,
    status            SMALLINT     NOT NULL DEFAULT 1,
    default_ttl_days  INT,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        BIGINT,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        BIGINT,
    CONSTRAINT pk_sys_package PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_package IS '套餐包定义（纯平台表）';
COMMENT ON COLUMN sys_package.code IS '套餐编码，全局唯一';
COMMENT ON COLUMN sys_package.is_system IS '是否系统预置基础包（true=基础包不可改名/下架/归档/删除）';
COMMENT ON COLUMN sys_package.status IS '0=draft 草稿 / 1=active 上架 / 2=archived 归档（不可恢复）/ 3=inactive 下架';
COMMENT ON COLUMN sys_package.default_ttl_days IS '默认有效期（天）；NULL 表示永久';

CREATE UNIQUE INDEX uk_sys_package_code ON sys_package (code);
CREATE INDEX idx_sys_package_status ON sys_package (status);


CREATE TABLE sys_package_module
(
    id          BIGINT       NOT NULL,
    package_id  BIGINT       NOT NULL,
    module_code VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sys_package_module PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_package_module IS '套餐包 ↔ 业务模块（纯平台表）。module_code 对应 sys_business_module.code';
COMMENT ON COLUMN sys_package_module.module_code IS '业务模块编码，不强制外键（模块下线后历史绑定仍保留）';

CREATE UNIQUE INDEX uk_sys_package_module ON sys_package_module (package_id, module_code);
CREATE INDEX idx_sys_package_module_module ON sys_package_module (module_code);


-- ============================================================================
-- 13) 运营租户开通
-- ============================================================================
CREATE TABLE sys_password_reset_token
(
    id              BIGINT       NOT NULL,
    token           VARCHAR(64)  NOT NULL,
    user_id         BIGINT       NOT NULL,
    tenant_id       BIGINT       NOT NULL,
    tenant_code     VARCHAR(64)  NOT NULL,
    username_mask   VARCHAR(64)  NOT NULL,
    purpose         VARCHAR(32)  NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    used_at         TIMESTAMPTZ,
    invalidated_at  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      BIGINT       NOT NULL,
    CONSTRAINT pk_sys_pwd_reset_token PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_password_reset_token IS '密码重置令牌（平台表）。一次性，48h 有效；重新生成时旧 token 立即 invalidated';
COMMENT ON COLUMN sys_password_reset_token.token IS 'URL-safe 随机字符串（≥32字节 Base64URL），全局唯一';
COMMENT ON COLUMN sys_password_reset_token.username_mask IS '账号掩码（公开端点展示用）';
COMMENT ON COLUMN sys_password_reset_token.purpose IS 'init_admin=开通时首次设密码 / reset_admin=主管理员找回密码';
COMMENT ON COLUMN sys_password_reset_token.used_at IS '非 null 表示已使用（一次性）';
COMMENT ON COLUMN sys_password_reset_token.invalidated_at IS '非 null 表示已被"重新生成"操作提前作废';

CREATE UNIQUE INDEX uk_pwd_reset_token ON sys_password_reset_token (token);
CREATE INDEX idx_pwd_reset_user ON sys_password_reset_token (user_id, created_at DESC);


CREATE TABLE sys_tenant_seat_addon
(
    id          BIGINT       NOT NULL,
    tenant_id   BIGINT       NOT NULL,
    quantity    INT          NOT NULL,
    remark      VARCHAR(500),
    operator_id BIGINT       NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_sys_tenant_seat_addon PRIMARY KEY (id),
    CONSTRAINT ck_seat_addon_quantity CHECK (quantity >= 0)
);

COMMENT ON TABLE  sys_tenant_seat_addon IS '加购席位记录（平台表）。永久有效；v1 不做创建用户时席位上限校验';
COMMENT ON COLUMN sys_tenant_seat_addon.quantity IS '加购数量（0=未加购）';

CREATE INDEX idx_seat_addon_tenant ON sys_tenant_seat_addon (tenant_id);


-- ============================================================================
-- 14) 安全验证（MFA）：身份验证器绑定 + 登录二步验证开关（解耦，每用户一行）
-- ============================================================================
CREATE TABLE sys_mfa_config
(
    id                BIGINT       NOT NULL PRIMARY KEY,
    tenant_id         BIGINT       NOT NULL DEFAULT 0,
    user_id           BIGINT       NOT NULL,
    totp_secret       VARCHAR(128),
    enabled_at        TIMESTAMP,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    login_mfa_enabled BOOLEAN      NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE  sys_mfa_config IS '用户安全验证配置（平台表，每用户一行）：身份验证器绑定 + 登录二步验证开关';
COMMENT ON COLUMN sys_mfa_config.totp_secret IS 'TOTP 密钥密文（AES）；非空=已绑定身份验证器，独立于登录开关存续';
COMMENT ON COLUMN sys_mfa_config.login_mfa_enabled IS '登录二步验证开关；开启后登录通道服务端推导（已绑 TOTP→TOTP，否则邮箱）';
COMMENT ON COLUMN sys_mfa_config.enabled_at IS '登录二步验证开启时间；NULL=未开启';

CREATE UNIQUE INDEX uk_sys_mfa_config_user ON sys_mfa_config (user_id);


CREATE TABLE sys_mfa_recovery_code
(
    id         BIGINT       NOT NULL PRIMARY KEY,
    tenant_id  BIGINT       NOT NULL DEFAULT 0,
    user_id    BIGINT       NOT NULL,
    code_hash  VARCHAR(80)  NOT NULL,
    used_at    TIMESTAMP,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  sys_mfa_recovery_code IS 'MFA 一次性恢复码（哈希存储）。每次（重新）配置生成 8 组，旧码作废';
COMMENT ON COLUMN sys_mfa_recovery_code.code_hash IS '恢复码 SHA-256 hex';
COMMENT ON COLUMN sys_mfa_recovery_code.used_at IS '使用时间；非空=已用';

CREATE INDEX idx_sys_mfa_recovery_user ON sys_mfa_recovery_code (user_id);


-- ============================================================================
-- 15) 系统参数（种子数据见 V1.0.1）
-- ============================================================================
CREATE TABLE sys_setting
(
    id          BIGINT       NOT NULL,
    category    VARCHAR(40)  NOT NULL,
    key         VARCHAR(80)  NOT NULL,
    value       JSONB        NOT NULL,
    description VARCHAR(255),
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  BIGINT,
    CONSTRAINT pk_sys_setting PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_setting IS '运营全局系统参数（纯平台表）。读经 OpsSettingService 缓存 + 失效广播';
COMMENT ON COLUMN sys_setting.category IS '分组：platform_info / tenant_default / security_policy / health_score';
COMMENT ON COLUMN sys_setting.key IS '参数键，分组内唯一';
COMMENT ON COLUMN sys_setting.value IS '参数值（jsonb），调用方按 Class<T> 反序列化';

CREATE UNIQUE INDEX uk_sys_setting_category_key ON sys_setting (category, key);

-- ============================================================================
-- 16) 推荐角色模板
-- ============================================================================
CREATE TABLE sys_recommended_role_template
(
    id          BIGINT       NOT NULL,
    name        VARCHAR(128) NOT NULL,
    description TEXT,
    data_scope  SMALLINT     NOT NULL,
    status      SMALLINT     NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  BIGINT,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  BIGINT,
    CONSTRAINT pk_sys_recommended_role_template PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_recommended_role_template IS '推荐角色模板定义（纯平台表）';
COMMENT ON COLUMN sys_recommended_role_template.name IS '模板名，运营内部唯一';
COMMENT ON COLUMN sys_recommended_role_template.data_scope IS '默认数据范围（租户语义）：1=全部 / 2=本部门含下属 / 3=仅本人';
COMMENT ON COLUMN sys_recommended_role_template.status IS '0=draft 草稿 / 1=published 已发布 / 2=inactive 已下架';

CREATE UNIQUE INDEX uk_sys_rec_role_tpl_name ON sys_recommended_role_template (name);
CREATE INDEX idx_sys_rec_role_tpl_status ON sys_recommended_role_template (status);


CREATE TABLE sys_recommended_role_template_package
(
    id           BIGINT      NOT NULL,
    template_id  BIGINT      NOT NULL,
    package_code VARCHAR(64) NOT NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sys_rec_role_tpl_package PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_recommended_role_template_package IS '推荐角色模板 ↔ 关联套餐包（纯平台表）。package_code 对应 sys_package.code';
COMMENT ON COLUMN sys_recommended_role_template_package.package_code IS '套餐编码，不强制外键（套餐归档后历史关联仍保留，由 Service 层判定失效）';

CREATE UNIQUE INDEX uk_sys_rec_role_tpl_package ON sys_recommended_role_template_package (template_id, package_code);
CREATE INDEX idx_sys_rec_role_tpl_package_code ON sys_recommended_role_template_package (package_code);


CREATE TABLE sys_recommended_role_template_permission
(
    id              BIGINT       NOT NULL,
    template_id     BIGINT       NOT NULL,
    permission_code VARCHAR(128) NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sys_rec_role_tpl_permission PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_recommended_role_template_permission IS '推荐角色模板 ↔ 包含权限点（纯平台表）。permission_code 来自 SPI 内存注册（非 ops.*）';
COMMENT ON COLUMN sys_recommended_role_template_permission.permission_code IS '权限点编码，不强制外键（业务模块卸载后历史绑定仍保留，标"部分权限失效"）';

CREATE UNIQUE INDEX uk_sys_rec_role_tpl_permission ON sys_recommended_role_template_permission (template_id, permission_code);


CREATE TABLE sys_recommended_role_template_activation
(
    id              BIGINT    NOT NULL,
    template_id     BIGINT    NOT NULL,
    tenant_id       BIGINT    NOT NULL,
    created_role_id BIGINT,
    activated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_by    BIGINT,
    CONSTRAINT pk_sys_rec_role_tpl_activation PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_recommended_role_template_activation IS '推荐角色模板 ↔ 已启用租户（纯平台表，故 tenant_id 仅作数据列、不触发租户隔离注入）。租户端启用快照时写入，运营端只读';
COMMENT ON COLUMN sys_recommended_role_template_activation.created_role_id IS '租户端启用时在 sys_role 生成的快照角色 id';

CREATE INDEX idx_sys_rec_role_tpl_act_template ON sys_recommended_role_template_activation (template_id);
CREATE INDEX idx_sys_rec_role_tpl_act_tenant ON sys_recommended_role_template_activation (tenant_id);


-- ============================================================================
-- 17) 客户健康度（参数与模板种子见 V1.0.1）
-- ============================================================================
CREATE TABLE sys_tenant_health
(
    id           BIGINT       NOT NULL,
    tenant_id    BIGINT       NOT NULL,
    score        INT          NOT NULL,
    level        VARCHAR(16)  NOT NULL,
    prev_level   VARCHAR(16),
    risk_signals JSONB        NOT NULL DEFAULT '[]'::jsonb,
    metrics      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    computed_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sys_tenant_health PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_tenant_health IS '客户健康度每租户最新快照（纯平台表）';
COMMENT ON COLUMN sys_tenant_health.tenant_id IS '被评估租户 sys_tenant.id（每租户一行，唯一）';
COMMENT ON COLUMN sys_tenant_health.score IS '健康度评分 0~100（基准 100 扣减风险权重后 clamp）';
COMMENT ON COLUMN sys_tenant_health.level IS '分档：healthy 健康 / warning 关注 / danger 危险';
COMMENT ON COLUMN sys_tenant_health.prev_level IS '上一次计算的分档（用于总览卡片的环比增减箭头）';
COMMENT ON COLUMN sys_tenant_health.risk_signals IS '命中的风险信号数组 [{code,label,weight}]（快照，列表直接展示）';
COMMENT ON COLUMN sys_tenant_health.metrics IS '最近 30 天指标快照 {monthlyActiveMembers,totalMembers,...}';
COMMENT ON COLUMN sys_tenant_health.computed_at IS '本行评分计算时刻';

CREATE UNIQUE INDEX uk_sys_tenant_health_tenant ON sys_tenant_health (tenant_id);
CREATE INDEX idx_sys_tenant_health_level ON sys_tenant_health (level);


-- ============================================================================
-- 18) 工单系统（通知模板种子见 V1.0.1）
-- ============================================================================
CREATE TABLE sys_ticket_ticket
(
    id               BIGINT       NOT NULL,
    tenant_id        BIGINT       NOT NULL,
    ticket_no        VARCHAR(32)  NOT NULL,
    title            VARCHAR(256) NOT NULL,
    type             SMALLINT     NOT NULL,
    priority         SMALLINT     NOT NULL,
    status           SMALLINT     NOT NULL DEFAULT 0,
    submitter_id     BIGINT       NOT NULL,
    submitter_name   VARCHAR(64),
    assignee_id      BIGINT,
    assignee_name    VARCHAR(64),
    sla_due_at       TIMESTAMPTZ  NOT NULL,
    sla_warned       BOOLEAN      NOT NULL DEFAULT FALSE,
    sla_breached     BOOLEAN      NOT NULL DEFAULT FALSE,
    resolved_at      TIMESTAMPTZ,
    closed_at        TIMESTAMPTZ,
    close_reason     VARCHAR(512),
    last_activity_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       BIGINT,
    created_by_dept  BIGINT,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       BIGINT,
    deleted_at       TIMESTAMP,
    CONSTRAINT pk_sys_ticket_ticket PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_ticket_ticket IS '工单主表（租户隔离）';
COMMENT ON COLUMN sys_ticket_ticket.ticket_no IS '工单号 T-YYYYMMDD-NNNN（系统生成，全局唯一）';
COMMENT ON COLUMN sys_ticket_ticket.type IS '类型：0=报障 1=咨询 2=建议 3=投诉';
COMMENT ON COLUMN sys_ticket_ticket.priority IS '优先级：0=低 1=普通 2=重要 3=紧急';
COMMENT ON COLUMN sys_ticket_ticket.status IS '状态：0=待处理 1=处理中 2=已解决 3=已关闭';
COMMENT ON COLUMN sys_ticket_ticket.submitter_id IS '提交人 sys_user.id（租户成员）';
COMMENT ON COLUMN sys_ticket_ticket.assignee_id IS '处理人 sys_user.id（运营客服，认领后填）';
COMMENT ON COLUMN sys_ticket_ticket.sla_due_at IS 'SLA 截止时间（提交时按优先级算）';
COMMENT ON COLUMN sys_ticket_ticket.sla_warned IS '是否已发过 SLA 临近（80%）提醒';
COMMENT ON COLUMN sys_ticket_ticket.sla_breached IS '是否已超 SLA（已发超时提醒 + 列表标红）';
COMMENT ON COLUMN sys_ticket_ticket.resolved_at IS '标记已解决时刻（用于已解决 7 天自动关闭）';
COMMENT ON COLUMN sys_ticket_ticket.last_activity_at IS '最后更新（任何动作都刷新）';

CREATE UNIQUE INDEX uk_sys_ticket_ticket_no ON sys_ticket_ticket (tenant_id, ticket_no) WHERE deleted_at IS NULL;
CREATE INDEX idx_sys_ticket_ticket_tenant_status ON sys_ticket_ticket (tenant_id, status);
CREATE INDEX idx_sys_ticket_ticket_tenant_submitter ON sys_ticket_ticket (tenant_id, submitter_id);
-- 运营跨租户索引（无 tenant_id 前缀属「有意例外」，非违规）：运营客服「我处理的工单」跨全部租户按 assignee 查。
CREATE INDEX idx_sys_ticket_ticket_assignee ON sys_ticket_ticket (assignee_id) WHERE deleted_at IS NULL;
COMMENT ON INDEX idx_sys_ticket_ticket_assignee IS '运营跨租户：客服「我处理的工单」按 assignee_id 跨租户查询，故不带 tenant_id 前缀（有意例外）';
-- 运营跨租户索引：TicketSlaJob → runSlaScan 跨全部租户按 sla_due_at 扫描临近/超时工单，故不带 tenant_id 前缀。
CREATE INDEX idx_sys_ticket_ticket_sla_due ON sys_ticket_ticket (sla_due_at) WHERE deleted_at IS NULL;
COMMENT ON INDEX idx_sys_ticket_ticket_sla_due IS '运营跨租户：SLA 扫描 Job 按 sla_due_at 跨租户扫描，故不带 tenant_id 前缀（有意例外）';


CREATE TABLE sys_ticket_message
(
    id           BIGINT    NOT NULL,
    tenant_id    BIGINT    NOT NULL,
    ticket_id    BIGINT    NOT NULL,
    author_id    BIGINT    NOT NULL,
    author_name  VARCHAR(64),
    author_side  SMALLINT  NOT NULL,
    content      TEXT      NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   BIGINT,
    CONSTRAINT pk_sys_ticket_message PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_ticket_message IS '工单沟通记录（含首条提交内容）';
COMMENT ON COLUMN sys_ticket_message.author_side IS '0=租户提交方 1=运营客服';

CREATE INDEX idx_sys_ticket_message_ticket ON sys_ticket_message (tenant_id, ticket_id);


CREATE TABLE sys_ticket_attachment
(
    id          BIGINT       NOT NULL,
    tenant_id   BIGINT       NOT NULL,
    ticket_id   BIGINT       NOT NULL,
    message_id  BIGINT,
    file_id     VARCHAR(64)  NOT NULL,
    file_name   VARCHAR(256),
    size_bytes  BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  BIGINT,
    CONSTRAINT pk_sys_ticket_attachment PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_ticket_attachment IS '工单附件（关联 sys_file，business-only）';
COMMENT ON COLUMN sys_ticket_attachment.file_id IS 'sys_file 对外字符串 ID';

CREATE INDEX idx_sys_ticket_attachment_ticket ON sys_ticket_attachment (tenant_id, ticket_id);


-- ============================================================================
-- 19) 合规·超量导出审批
-- ============================================================================
CREATE TABLE sys_export_request
(
    id                  BIGINT       NOT NULL,
    tenant_id           BIGINT       NOT NULL,
    request_no          VARCHAR(32)  NOT NULL,
    object_type         VARCHAR(64)  NOT NULL,
    object_code         VARCHAR(64),
    filter_summary      VARCHAR(512),
    estimated_count     INTEGER      NOT NULL,
    reason              VARCHAR(512) NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    applicant_id        BIGINT       NOT NULL,
    applicant_name      VARCHAR(64),
    applicant_dept_name VARCHAR(128),
    approver_id         BIGINT,
    approver_name       VARCHAR(64),
    approval_comment    VARCHAR(512),
    -- 审批通过后的执行衔接
    execute_params      JSONB,
    task_id             BIGINT,
    applied_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at          TIMESTAMPTZ,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT,
    deleted_at          TIMESTAMP,
    CONSTRAINT pk_sys_export_request PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_export_request IS '超量导出审批台账（混合表，tenant_id 隔离）';
COMMENT ON COLUMN sys_export_request.request_no IS '申请编号 EX-YYYYMMDD-NNNN（租户内当日序号）';
COMMENT ON COLUMN sys_export_request.object_type IS '导出对象显示名，如"客户列表"、"操作日志"';
COMMENT ON COLUMN sys_export_request.object_code IS '导出对象机器码（业务模块声明，可空）';
COMMENT ON COLUMN sys_export_request.filter_summary IS '筛选条件文字摘要（时间范围 / 状态等）';
COMMENT ON COLUMN sys_export_request.estimated_count IS '预估导出条数（系统计算）';
COMMENT ON COLUMN sys_export_request.reason IS '申请理由（必填）';
COMMENT ON COLUMN sys_export_request.status IS '审批状态：PENDING / APPROVED / REJECTED';
COMMENT ON COLUMN sys_export_request.approver_id IS '审批人 sys_user.id（决策后填）';
COMMENT ON COLUMN sys_export_request.approval_comment IS '审批意见（选填）';
COMMENT ON COLUMN sys_export_request.execute_params IS '执行参数快照：filters + 申请人数据权限语境';
COMMENT ON COLUMN sys_export_request.task_id IS '审批通过后投递的任务中心任务 ID';

CREATE UNIQUE INDEX uk_sys_export_request_no ON sys_export_request (tenant_id, request_no) WHERE deleted_at IS NULL;
CREATE INDEX idx_sys_export_request_status ON sys_export_request (tenant_id, status);
CREATE INDEX idx_sys_export_request_applicant ON sys_export_request (tenant_id, applicant_id);
CREATE INDEX idx_sys_export_request_applied ON sys_export_request (applied_at);


CREATE TABLE sys_export_policy
(
    id                 BIGINT      NOT NULL,
    tenant_id          BIGINT      NOT NULL,
    approval_authority VARCHAR(20) NOT NULL DEFAULT 'MAIN_ADMIN',
    timeout_hours      INTEGER,
    updated_at         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         BIGINT,
    CONSTRAINT pk_sys_export_policy PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_export_policy IS '超量导出策略（每租户单行，审批按对象开关）';
COMMENT ON COLUMN sys_export_policy.approval_authority IS '审批权限：MAIN_ADMIN / ROLE_PERMISSION';
COMMENT ON COLUMN sys_export_policy.timeout_hours IS '审批超时提醒（小时，NULL=不提醒）';

CREATE UNIQUE INDEX uk_sys_export_policy_tenant ON sys_export_policy (tenant_id);


-- 导出对象审批开关覆盖
CREATE TABLE sys_export_approval_rule
(
    id                BIGINT      NOT NULL,
    tenant_id         BIGINT      NOT NULL,
    object_code       VARCHAR(64) NOT NULL,
    requires_approval BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        BIGINT,
    CONSTRAINT pk_sys_export_approval_rule PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_export_approval_rule IS '导出对象审批开关覆盖（每租户每对象一行）';
COMMENT ON COLUMN sys_export_approval_rule.object_code IS '导出对象机器码（ExportObjectContributor 声明，如 member / audit_log）';
COMMENT ON COLUMN sys_export_approval_rule.requires_approval IS '该对象是否需审批（覆盖业务声明默认值）';

CREATE UNIQUE INDEX uk_sys_export_approval_rule ON sys_export_approval_rule (tenant_id, object_code);


-- ============================================================================
-- 20) 合规·客户全量数据导出（通知模板种子见 V1.0.1）
-- ============================================================================
CREATE TABLE sys_data_export_request
(
    id             BIGINT       NOT NULL,
    tenant_id      BIGINT       NOT NULL,
    request_no     VARCHAR(32)  NOT NULL,
    reason         VARCHAR(512) NOT NULL,
    data_mode      VARCHAR(16)  NOT NULL DEFAULT 'MASKED',
    scope_summary  VARCHAR(512),
    status         VARCHAR(20)  NOT NULL DEFAULT 'PACKAGING',
    item_summary   VARCHAR(1024),
    file_name      VARCHAR(255),
    file_size      BIGINT,
    download_token VARCHAR(64),
    -- 异步打包执行衔接
    file_id        VARCHAR(32),
    task_id        BIGINT,
    expires_at     TIMESTAMPTZ,
    applicant_id   BIGINT       NOT NULL,
    applicant_name VARCHAR(64),
    requested_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at   TIMESTAMPTZ,
    error_message  VARCHAR(512),
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     BIGINT,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     BIGINT,
    deleted_at     TIMESTAMP,
    CONSTRAINT pk_sys_data_export_request PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_data_export_request IS '客户全量数据导出申请台账（混合表，tenant_id 隔离）';
COMMENT ON COLUMN sys_data_export_request.request_no IS '申请编号 DX-YYYYMMDD-NNNN（租户内当日序号）';
COMMENT ON COLUMN sys_data_export_request.reason IS '申请理由（必填，如销户 / 合规审计 / 数据迁移）';
COMMENT ON COLUMN sys_data_export_request.data_mode IS '敏感字段处理：FULL=完整 / MASKED=脱敏';
COMMENT ON COLUMN sys_data_export_request.scope_summary IS '导出范围文字摘要（组织 / 成员 / 字典 / 文件 / 操作日志 / 业务数据）';
COMMENT ON COLUMN sys_data_export_request.status IS '状态：PACKAGING / COMPLETED / FAILED';
COMMENT ON COLUMN sys_data_export_request.item_summary IS '打包内容概要（各类目计数 JSON 文本快照）';
COMMENT ON COLUMN sys_data_export_request.file_name IS '生成的 zip 包文件名';
COMMENT ON COLUMN sys_data_export_request.file_size IS '生成的 zip 包字节数（估算）';
COMMENT ON COLUMN sys_data_export_request.download_token IS '下载令牌（占位，待文件中心接入）';
COMMENT ON COLUMN sys_data_export_request.file_id IS '文件中心 fileId（打包完成后写，下载端点据此换签名 URL）';
COMMENT ON COLUMN sys_data_export_request.task_id IS '关联任务中心 sys_task.id（异步打包任务，供前端查进度）';
COMMENT ON COLUMN sys_data_export_request.expires_at IS '下载链接失效时间（完成后 7 天）';
COMMENT ON COLUMN sys_data_export_request.applicant_id IS '发起人 sys_user.id（仅主管理员）';

CREATE UNIQUE INDEX uk_sys_data_export_request_no ON sys_data_export_request (tenant_id, request_no) WHERE deleted_at IS NULL;
CREATE INDEX idx_sys_data_export_request_status ON sys_data_export_request (tenant_id, status);
CREATE INDEX idx_sys_data_export_request_at ON sys_data_export_request (tenant_id, requested_at);


-- ============================================================================
-- 21) 平台运维监控
-- ============================================================================
CREATE TABLE sys_tenant_renewal_log
(
    id            BIGINT      NOT NULL PRIMARY KEY,
    tenant_id     BIGINT      NOT NULL,
    package_code  VARCHAR(64) NOT NULL,
    event_type    VARCHAR(16) NOT NULL,
    old_expire_at TIMESTAMP,
    new_expire_at TIMESTAMP,
    ttl_days      INTEGER,
    operator_id   BIGINT,
    created_at    TIMESTAMP   NOT NULL DEFAULT now()
);

COMMENT ON TABLE  sys_tenant_renewal_log IS '租户套餐授予/续费/撤销台账（运营操作产生）';
COMMENT ON COLUMN sys_tenant_renewal_log.tenant_id IS '目标租户（数据归属，非过滤维度）';
COMMENT ON COLUMN sys_tenant_renewal_log.event_type IS 'grant=授予 / renew=续费 / revoke=撤销';
COMMENT ON COLUMN sys_tenant_renewal_log.old_expire_at IS '变更前到期时间；授予为 NULL';
COMMENT ON COLUMN sys_tenant_renewal_log.new_expire_at IS '变更后到期时间；撤销为 NULL';
COMMENT ON COLUMN sys_tenant_renewal_log.ttl_days IS '本次授予/续费的有效天数；撤销为 NULL';
COMMENT ON COLUMN sys_tenant_renewal_log.operator_id IS '运营操作人 user_id';

CREATE INDEX idx_renewal_tenant_time ON sys_tenant_renewal_log (tenant_id, created_at DESC);
CREATE INDEX idx_renewal_event_time ON sys_tenant_renewal_log (event_type, created_at DESC);


CREATE TABLE sys_module_access_log
(
    id          BIGINT      NOT NULL,
    tenant_id   BIGINT      NOT NULL DEFAULT 0,
    module_code VARCHAR(64) NOT NULL,
    operator_id BIGINT,
    method      VARCHAR(10),
    path        VARCHAR(500),
    status_code INTEGER,
    duration_ms INTEGER,
    created_at  TIMESTAMP   NOT NULL,
    CONSTRAINT pk_sys_module_access_log PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

COMMENT ON TABLE  sys_module_access_log IS '业务模块访问埋点（混合表 + 月度分区）。命中业务模块前缀的所有请求（含查询读）';
COMMENT ON COLUMN sys_module_access_log.tenant_id IS '访问者租户；0=运营/匿名（数据归属，非过滤维度）';
COMMENT ON COLUMN sys_module_access_log.module_code IS '业务模块编码（ModuleApiRouteRegistry 最长前缀匹配得出）';
COMMENT ON COLUMN sys_module_access_log.operator_id IS '访问用户 user_id（去重算模块 DAU）；匿名为 NULL';

CREATE INDEX idx_modaccess_module_time ON sys_module_access_log (module_code, created_at DESC);
CREATE INDEX idx_modaccess_tenant_time ON sys_module_access_log (tenant_id, created_at DESC);

CREATE TABLE sys_module_access_log_p202605 PARTITION OF sys_module_access_log
    FOR VALUES FROM ('2026-05-01 00:00:00') TO ('2026-06-01 00:00:00');
CREATE TABLE sys_module_access_log_p202606 PARTITION OF sys_module_access_log
    FOR VALUES FROM ('2026-06-01 00:00:00') TO ('2026-07-01 00:00:00');
CREATE TABLE sys_module_access_log_p202607 PARTITION OF sys_module_access_log
    FOR VALUES FROM ('2026-07-01 00:00:00') TO ('2026-08-01 00:00:00');
CREATE TABLE sys_module_access_log_p202608 PARTITION OF sys_module_access_log
    FOR VALUES FROM ('2026-08-01 00:00:00') TO ('2026-09-01 00:00:00');
CREATE TABLE sys_module_access_log_p202609 PARTITION OF sys_module_access_log
    FOR VALUES FROM ('2026-09-01 00:00:00') TO ('2026-10-01 00:00:00');
CREATE TABLE sys_module_access_log_p202610 PARTITION OF sys_module_access_log
    FOR VALUES FROM ('2026-10-01 00:00:00') TO ('2026-11-01 00:00:00');
CREATE TABLE sys_module_access_log_default PARTITION OF sys_module_access_log DEFAULT;


-- ============================================================================
-- 22) 租户子域名别名
-- ============================================================================
CREATE TABLE sys_tenant_route_alias
(
    id         BIGINT      NOT NULL,
    tenant_id  BIGINT      NOT NULL,
    old_code   VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP   NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

COMMENT ON TABLE  sys_tenant_route_alias IS '租户子域名（code）变更别名：旧 code 90 天内重定向到租户当前 code';
COMMENT ON COLUMN sys_tenant_route_alias.old_code IS '变更前的旧子域名 code（全局唯一；reclaim 时 upsert 覆盖）';
COMMENT ON COLUMN sys_tenant_route_alias.expires_at IS '别名失效时间（变更时 = now + 90 天）';

CREATE UNIQUE INDEX uk_route_alias_old_code ON sys_tenant_route_alias (old_code);
CREATE INDEX idx_route_alias_tenant ON sys_tenant_route_alias (tenant_id);


-- ============================================================================
-- 23) 菜单树（种子数据见 V1.0.1）
-- ============================================================================
-- sys_menu 为菜单树唯一权威：平台全局（无 tenant_id），后端代码不声明菜单，
-- 初始数据由 V1.0.1 一次性种子，此后由运营「菜单管理」独占维护。
-- 节点类型 node_type：1=GROUP 分节容器 / 2=PAGE 导航页 / 3=ACTION 动作码。
-- scope：1=OPS / 2=TENANT / 3=H5（H5 预留、暂无种子）。
CREATE TABLE sys_menu
(
    id              bigint        NOT NULL,
    scope           smallint      NOT NULL,
    node_key        varchar(128)  NOT NULL,
    parent_key      varchar(128),
    node_type       smallint      NOT NULL,
    label           varchar(128)  NOT NULL,
    icon            varchar(64),
    path            varchar(256),
    permission_code varchar(128),
    description     varchar(256),
    sort            int           NOT NULL DEFAULT 0,
    source          smallint      NOT NULL DEFAULT 1,
    status          smallint      NOT NULL DEFAULT 1,
    dangling        boolean       NOT NULL DEFAULT false,
    created_at      timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sys_menu PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_sys_menu_scope_key ON sys_menu (scope, node_key);
CREATE INDEX idx_sys_menu_scope_parent_sort ON sys_menu (scope, parent_key, sort);
CREATE INDEX idx_sys_menu_permission_code ON sys_menu (permission_code);

COMMENT ON TABLE  sys_menu IS '菜单树（纯平台表，无 tenant_id）。DB 唯一权威，运营「菜单管理」维护；与权限码经 permission_code 相连';
COMMENT ON COLUMN sys_menu.scope IS '端：1=OPS / 2=TENANT / 3=H5';
COMMENT ON COLUMN sys_menu.node_key IS '稳定键，(scope,node_key) 唯一';
COMMENT ON COLUMN sys_menu.node_type IS '1=GROUP 分节容器（无 path/code）/ 2=PAGE 导航页 / 3=ACTION 动作码（挂 PAGE 下）';
COMMENT ON COLUMN sys_menu.permission_code IS 'PAGE 的 gate 码 / ACTION 的码；GROUP 恒 null；null 的 PAGE 对所有人可见';
COMMENT ON COLUMN sys_menu.source IS '1=SEED（仅可停用）/ 2=ADMIN（可删）';
COMMENT ON COLUMN sys_menu.status IS '1=ENABLED / 0=DISABLED';
COMMENT ON COLUMN sys_menu.dangling IS '启动校验置位：permission_code 不在后端权威目录（悬空失效），菜单管理红标';


-- ============================================================================
-- 24) MQ 死信兜底
-- ============================================================================
-- 重试耗尽 + 毒丸快失两条路径均由消费链路（MqConsumeSupport）内落库，无独立 DLQ 监控进程。
-- 平台表：无租户上下文写入，必须进 platform-only-tables 白名单（TenantLine 跳过 tenant_id 注入）。
CREATE TABLE sys_mq_dead_letter
(
    id                BIGINT       NOT NULL PRIMARY KEY,           -- 雪花 ID
    consumer_group    VARCHAR(128) NOT NULL,                       -- 源消费组
    original_topic    VARCHAR(128) NOT NULL,                       -- 原 topic
    msg_id            VARCHAR(64),                                 -- broker msgId（毒丸路径可空）
    msg_keys          VARCHAR(255),                                -- 业务 keys（如有）
    tenant_id         BIGINT       NOT NULL DEFAULT 0,             -- 从 body 尽力解析；解析不出填 0（纯过滤列，不做隔离注入）
    body              TEXT,                                        -- 消息体（截断 body-truncate-bytes）
    reconsume_times   INTEGER,                                     -- 已投递次数（getDeliveryAttempt；毒丸路径可空）
    born_at           TIMESTAMP,                                   -- 消息产生时间
    exception_summary TEXT,                                        -- 源头捕获的异常摘要（可空）
    source            VARCHAR(24)  NOT NULL,                       -- retry_exhausted | poison_fast_fail
    status            VARCHAR(16)  NOT NULL DEFAULT 'new',         -- new | requeued | ignored（运维重投/忽略，重投入口留 v2）
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    handled_at        TIMESTAMP,
    handled_by        BIGINT
);
COMMENT ON TABLE  sys_mq_dead_letter IS 'MQ 死信兜底表：重试耗尽 + 毒丸快失两条路径均由消费链路内落库（无独立 DLQ 监控），供运维查询/重投/告警';
COMMENT ON COLUMN sys_mq_dead_letter.consumer_group IS '源消费组（CLUSTERING 组；广播组无 DLQ 不纳入）';
COMMENT ON COLUMN sys_mq_dead_letter.original_topic IS '原 topic（毒丸路径为消费者订阅 topic）';
COMMENT ON COLUMN sys_mq_dead_letter.tenant_id IS '从 body 尽力解析的归属租户，解析失败填 0；纯过滤列，不被 TenantLine 注入';
COMMENT ON COLUMN sys_mq_dead_letter.source IS 'retry_exhausted=瞬时异常重试到 max-delivery-attempts 仍失败后落库；poison_fast_fail=消费侧判定不可重试直接落库';
COMMENT ON COLUMN sys_mq_dead_letter.status IS 'new=待处理 / requeued=已重投 / ignored=已忽略（重投入口 v2）';

CREATE INDEX idx_mq_dlq_group_created ON sys_mq_dead_letter (consumer_group, created_at DESC);
CREATE INDEX idx_mq_dlq_status ON sys_mq_dead_letter (status) WHERE status = 'new';
CREATE INDEX idx_mq_dlq_tenant ON sys_mq_dead_letter (tenant_id, created_at DESC);


-- ============================================================================
-- 25) 用户备忘便利贴
-- ============================================================================
-- 个人自助，作用域 (tenant_id, user_id)；TenantLine 拦截器按 tenant_id 自动隔离。
-- 列表按 updated_at 倒序（最近新建/编辑排最前），索引与之对齐；展示的「更新于」时间即排序键。
-- 会话内不即时冒顶由前端冻结顺序实现，服务端只负责一致的排序口径。
CREATE TABLE sys_sticky_note
(
    id         BIGINT      NOT NULL,
    tenant_id  BIGINT      NOT NULL DEFAULT 0,
    user_id    BIGINT      NOT NULL,
    content    TEXT        NOT NULL,
    color      VARCHAR(32) NOT NULL DEFAULT 'yellow',
    created_at TIMESTAMP   NOT NULL,
    updated_at TIMESTAMP   NOT NULL,
    CONSTRAINT pk_sys_sticky_note PRIMARY KEY (id)
);

CREATE INDEX idx_sys_sticky_note_owner
    ON sys_sticky_note (tenant_id, user_id, updated_at DESC);

COMMENT ON TABLE sys_sticky_note IS '用户级备忘便利贴';


-- ============================================================================
-- 26) 数据变更日志 ③（触发器函数与挂载在本文件末尾，列元数据种子见 V1.0.1）
-- ============================================================================
CREATE TABLE sys_audit_table_config
(
    table_name  VARCHAR(80)  NOT NULL,
    column_name VARCHAR(80)  NOT NULL,
    label       VARCHAR(120),
    render      VARCHAR(40)  NOT NULL DEFAULT 'SCALAR',
    CONSTRAINT pk_sys_audit_table_config PRIMARY KEY (table_name, column_name)
);
COMMENT ON TABLE  sys_audit_table_config IS '③ 数据变更日志列元数据：触发器读"怎么记"、前端读"怎么译"';
COMMENT ON COLUMN sys_audit_table_config.column_name IS '''*''=表级登记行；其余=列级覆盖';
COMMENT ON COLUMN sys_audit_table_config.render IS 'SCALAR|ENUM:dict|REF:entityType|MASK|EXCLUDE';


CREATE SEQUENCE sys_data_change_log_id_seq;

CREATE TABLE sys_data_change_log
(
    id         BIGINT       NOT NULL DEFAULT nextval('sys_data_change_log_id_seq'),
    request_id VARCHAR(64),
    actor_id   BIGINT,
    tenant_id  BIGINT,
    table_name VARCHAR(80)  NOT NULL,
    op         VARCHAR(10)  NOT NULL,
    row_pk     VARCHAR(80),
    changes    JSONB        NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sys_data_change_log PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

COMMENT ON TABLE  sys_data_change_log IS '③ 数据变更日志（触发器写入 + 月分区）。原始取证，人话由展示层按列元数据渲染';
COMMENT ON COLUMN sys_data_change_log.changes IS '{列:{before,after}}；脱敏列只记 "<已变更>"';

CREATE INDEX idx_dclog_request ON sys_data_change_log (request_id);
CREATE INDEX idx_dclog_row ON sys_data_change_log (table_name, row_pk, created_at DESC);
CREATE INDEX idx_dclog_tenant_time ON sys_data_change_log (tenant_id, created_at DESC);

CREATE TABLE sys_data_change_log_p202605 PARTITION OF sys_data_change_log
    FOR VALUES FROM ('2026-05-01 00:00:00') TO ('2026-06-01 00:00:00');
CREATE TABLE sys_data_change_log_p202606 PARTITION OF sys_data_change_log
    FOR VALUES FROM ('2026-06-01 00:00:00') TO ('2026-07-01 00:00:00');
CREATE TABLE sys_data_change_log_p202607 PARTITION OF sys_data_change_log
    FOR VALUES FROM ('2026-07-01 00:00:00') TO ('2026-08-01 00:00:00');
CREATE TABLE sys_data_change_log_p202608 PARTITION OF sys_data_change_log
    FOR VALUES FROM ('2026-08-01 00:00:00') TO ('2026-09-01 00:00:00');
CREATE TABLE sys_data_change_log_p202609 PARTITION OF sys_data_change_log
    FOR VALUES FROM ('2026-09-01 00:00:00') TO ('2026-10-01 00:00:00');
CREATE TABLE sys_data_change_log_default PARTITION OF sys_data_change_log DEFAULT;


-- 通用审计触发器函数（供下方各 CREATE TRIGGER 挂载复用）
CREATE OR REPLACE FUNCTION sys_audit_row_change() RETURNS trigger AS
$$
DECLARE
    c_global_excl CONSTANT text[] := ARRAY['created_at','created_by','updated_at','updated_by','deleted_at','version'];
    v_old     jsonb;
    v_new     jsonb;
    v_excl    text[];
    v_mask    text[];
    v_changes jsonb := '{}'::jsonb;
    v_key     text;
    v_before  jsonb;
    v_after   jsonb;
    v_op      text := TG_OP;
    v_pk      text;
    v_tenant  bigint;
    v_req     text := nullif(current_setting('app.request_id', true), '');
    v_actor   text := nullif(current_setting('app.actor_id', true), '');
    v_tctx    text := nullif(current_setting('app.tenant_id', true), '');
BEGIN
    SELECT array_agg(column_name) FILTER (WHERE render = 'EXCLUDE'),
           array_agg(column_name) FILTER (WHERE render = 'MASK')
      INTO v_excl, v_mask
      FROM sys_audit_table_config
     WHERE table_name = TG_TABLE_NAME;

    v_old := CASE WHEN TG_OP = 'INSERT' THEN '{}'::jsonb ELSE to_jsonb(OLD) END;
    v_new := CASE WHEN TG_OP = 'DELETE' THEN '{}'::jsonb ELSE to_jsonb(NEW) END;

    IF TG_OP = 'UPDATE'
       AND (v_old ->> 'deleted_at') IS NULL
       AND (v_new ->> 'deleted_at') IS NOT NULL THEN
        v_op := 'DELETE';
        v_new := '{}'::jsonb;
    END IF;

    FOR v_key IN
        SELECT k FROM (
            SELECT jsonb_object_keys(v_old) AS k
            UNION
            SELECT jsonb_object_keys(v_new)
        ) s
    LOOP
        IF v_key = ANY (c_global_excl) THEN CONTINUE; END IF;
        IF v_excl IS NOT NULL AND v_key = ANY (v_excl) THEN CONTINUE; END IF;

        v_before := v_old -> v_key;
        v_after := v_new -> v_key;
        IF v_before IS DISTINCT FROM v_after THEN
            IF v_mask IS NOT NULL AND v_key = ANY (v_mask) THEN
                v_changes := v_changes || jsonb_build_object(v_key,
                    jsonb_build_object('before', CASE WHEN v_before IS NULL THEN NULL ELSE to_jsonb('<已变更>'::text) END,
                                       'after',  CASE WHEN v_after  IS NULL THEN NULL ELSE to_jsonb('<已变更>'::text) END));
            ELSE
                v_changes := v_changes || jsonb_build_object(v_key,
                    jsonb_build_object('before', v_before, 'after', v_after));
            END IF;
        END IF;
    END LOOP;

    IF v_changes = '{}'::jsonb THEN
        RETURN NULL;
    END IF;

    v_pk := COALESCE(v_new ->> 'id', v_old ->> 'id');
    BEGIN
        v_tenant := COALESCE(v_tctx::bigint, (COALESCE(v_new ->> 'tenant_id', v_old ->> 'tenant_id'))::bigint);
    EXCEPTION WHEN others THEN
        v_tenant := NULL;
    END;

    INSERT INTO sys_data_change_log(request_id, actor_id, tenant_id, table_name, op, row_pk, changes)
    VALUES (v_req,
            CASE WHEN v_actor ~ '^\d+$' THEN v_actor::bigint ELSE NULL END,
            v_tenant, TG_TABLE_NAME, v_op, v_pk, v_changes);

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION sys_audit_row_change() IS '③ 通用变更触发器：感知/脱敏分离、软删归一、读 GUC 上下文';


-- 挂载审计触发器（AFTER 行级，复用 sys_audit_row_change()；绝不挂日志表自身防递归）
-- 列元数据 sys_audit_table_config 的种子在 V1.0.1 最前面写入，先于初始化数据 → 审计脱敏/排除规则即时生效。
CREATE TRIGGER trg_audit_sys_role
    AFTER INSERT OR UPDATE OR DELETE ON sys_role
    FOR EACH ROW EXECUTE FUNCTION sys_audit_row_change();
CREATE TRIGGER trg_audit_sys_role_permission
    AFTER INSERT OR UPDATE OR DELETE ON sys_role_permission
    FOR EACH ROW EXECUTE FUNCTION sys_audit_row_change();
CREATE TRIGGER trg_audit_sys_tenant
    AFTER INSERT OR UPDATE OR DELETE ON sys_tenant
    FOR EACH ROW EXECUTE FUNCTION sys_audit_row_change();
CREATE TRIGGER trg_audit_sys_package
    AFTER INSERT OR UPDATE OR DELETE ON sys_package
    FOR EACH ROW EXECUTE FUNCTION sys_audit_row_change();
CREATE TRIGGER trg_audit_sys_package_module
    AFTER INSERT OR UPDATE OR DELETE ON sys_package_module
    FOR EACH ROW EXECUTE FUNCTION sys_audit_row_change();
CREATE TRIGGER trg_audit_sys_user
    AFTER INSERT OR UPDATE OR DELETE ON sys_user
    FOR EACH ROW EXECUTE FUNCTION sys_audit_row_change();
CREATE TRIGGER trg_audit_sys_user_role
    AFTER INSERT OR UPDATE OR DELETE ON sys_user_role
    FOR EACH ROW EXECUTE FUNCTION sys_audit_row_change();
CREATE TRIGGER trg_audit_sys_dept
    AFTER INSERT OR UPDATE OR DELETE ON sys_dept
    FOR EACH ROW EXECUTE FUNCTION sys_audit_row_change();
CREATE TRIGGER trg_audit_sys_user_dept
    AFTER INSERT OR UPDATE OR DELETE ON sys_user_dept
    FOR EACH ROW EXECUTE FUNCTION sys_audit_row_change();
CREATE TRIGGER trg_audit_sys_storage_config
    AFTER INSERT OR UPDATE OR DELETE ON sys_storage_config
    FOR EACH ROW EXECUTE FUNCTION sys_audit_row_change();
CREATE TRIGGER trg_audit_sys_setting
    AFTER INSERT OR UPDATE OR DELETE ON sys_setting
    FOR EACH ROW EXECUTE FUNCTION sys_audit_row_change();
CREATE TRIGGER trg_audit_sys_dict_group
    AFTER INSERT OR UPDATE OR DELETE ON sys_dict_group
    FOR EACH ROW EXECUTE FUNCTION sys_audit_row_change();
CREATE TRIGGER trg_audit_sys_dict_item
    AFTER INSERT OR UPDATE OR DELETE ON sys_dict_item
    FOR EACH ROW EXECUTE FUNCTION sys_audit_row_change();
CREATE TRIGGER trg_audit_sys_notification_template
    AFTER INSERT OR UPDATE OR DELETE ON sys_notification_template
    FOR EACH ROW EXECUTE FUNCTION sys_audit_row_change();

-- ============================================================================
-- 表结构（含审计函数与触发器）到此结束。种子数据见 V1.0.1__init_data.sql
-- ============================================================================
