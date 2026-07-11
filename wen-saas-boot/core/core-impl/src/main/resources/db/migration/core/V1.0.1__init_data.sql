-- ============================================================================
-- Wen SaaS Boot 初始化种子数据（V1.0.1）
--
-- 审计列元数据 / 字典 / 套餐 / 运营账号与角色 / 菜单树 / 系统参数 / 通知模板。
-- 全部 INSERT 采用 ON CONFLICT DO NOTHING 幂等写法。
--
-- 审计触发器（CREATE TRIGGER）随表结构定义在 V1.0.0__init_schema.sql；本文件最先写入审计列
-- 元数据 sys_audit_table_config，使初始化数据触发审计时 MASK/EXCLUDE 规则即时生效，初始化产生的
-- 变更会正常记入 sys_data_change_log（作为基线审计痕迹，符合"该被审计的变动记录完整"的预期）。
-- ============================================================================


-- ============================================================================
-- 0) 审计列元数据（最先写入：先于其余初始化数据，使审计脱敏/排除规则即时生效）
--    FK 引用列（sys_role_permission.role_id / sys_package_module.package_id /
--    sys_tenant.csm_user_id）不在此登记，运行时由实体 @DisplayRef 驱动展示。
-- ============================================================================
INSERT INTO sys_audit_table_config(table_name, column_name, label, render) VALUES
    -- 角色
    ('sys_role', '*',          NULL,       'SCALAR'),
    ('sys_role', 'name',       '角色名',   'SCALAR'),
    ('sys_role', 'code',       '角色编码', 'SCALAR'),
    ('sys_role', 'status',     '状态',     'ENUM:commonStatus'),
    ('sys_role', 'description','描述',     'SCALAR'),
    -- 角色权限
    ('sys_role_permission', '*',               NULL,   'SCALAR'),
    ('sys_role_permission', 'permission_code', '权限', 'SCALAR'),
    -- 租户
    ('sys_tenant', '*',           NULL,       'SCALAR'),
    ('sys_tenant', 'name',        '租户名',   'SCALAR'),
    ('sys_tenant', 'status',      '状态',     'ENUM:tenantStatus'),
    ('sys_tenant', 'expire_at',   '到期时间', 'SCALAR'),
    ('sys_tenant', 'member_cap',  '成员上限', 'SCALAR'),
    ('sys_tenant', 'storage_cap', '存储上限', 'SCALAR'),
    -- 套餐
    ('sys_package', '*',                NULL,            'SCALAR'),
    ('sys_package', 'name',             '套餐名',        'SCALAR'),
    ('sys_package', 'code',             '套餐编码',      'SCALAR'),
    ('sys_package', 'status',           '状态',          'ENUM:packageStatus'),
    ('sys_package', 'default_ttl_days', '默认有效期(天)', 'SCALAR'),
    ('sys_package', 'description',      '描述',          'SCALAR'),
    -- 套餐-模块
    ('sys_package_module', '*',           NULL,   'SCALAR'),
    ('sys_package_module', 'module_code', '模块', 'SCALAR'),
    -- 用户主表
    ('sys_user', '*',             NULL,       'SCALAR'),
    ('sys_user', 'username',      '登录账号', 'SCALAR'),
    ('sys_user', 'real_name',     '姓名',     'SCALAR'),
    ('sys_user', 'emp_no',        '员工编号', 'SCALAR'),
    ('sys_user', 'status',        '状态',     'ENUM:commonStatus'),
    ('sys_user', 'gender',        '性别',     'ENUM:gender'),
    ('sys_user', 'password_hash', '密码',     'MASK'),
    ('sys_user', 'mobile',        '手机号',   'MASK'),
    ('sys_user', 'email',         '邮箱',     'MASK'),
    ('sys_user', 'last_login_at', NULL,       'EXCLUDE'),
    ('sys_user', 'avatar_file_id',NULL,       'EXCLUDE'),
    -- 用户→角色
    ('sys_user_role', '*', NULL, 'SCALAR'),
    -- 部门
    ('sys_dept', '*',          NULL,     'SCALAR'),
    ('sys_dept', 'name',       '部门名', 'SCALAR'),
    ('sys_dept', 'status',     '状态',   'ENUM:commonStatus'),
    ('sys_dept', 'sort_order', '排序',   'SCALAR'),
    ('sys_dept', 'ancestors',  NULL,     'EXCLUDE'),
    -- 用户→部门
    ('sys_user_dept', '*',       NULL,     'SCALAR'),
    ('sys_user_dept', 'is_main', '主部门', 'SCALAR'),
    -- 存储配置
    ('sys_storage_config', '*',                 NULL,             'SCALAR'),
    ('sys_storage_config', 'backend_type',      '存储类型',       'SCALAR'),
    ('sys_storage_config', 'endpoint',          'Endpoint',       'SCALAR'),
    ('sys_storage_config', 'bucket',            'Bucket',         'SCALAR'),
    ('sys_storage_config', 'path_prefix',       '路径前缀',       'SCALAR'),
    ('sys_storage_config', 'status',            '状态',           'SCALAR'),
    ('sys_storage_config', 'access_key_id',     'AccessKeyId',    'MASK'),
    ('sys_storage_config', 'access_key_secret', 'AccessKeySecret','MASK'),
    -- 系统参数
    ('sys_setting', '*',        NULL,     'SCALAR'),
    ('sys_setting', 'category', '分组',   'SCALAR'),
    ('sys_setting', 'key',      '参数键', 'SCALAR'),
    ('sys_setting', 'value',    '参数值', 'SCALAR'),
    -- 字典分组
    ('sys_dict_group', '*',           NULL,     'SCALAR'),
    ('sys_dict_group', 'code',        '分组编码', 'SCALAR'),
    ('sys_dict_group', 'name',        '分组名',   'SCALAR'),
    ('sys_dict_group', 'status',      '状态',     'ENUM:commonStatus'),
    ('sys_dict_group', 'description', '描述',     'SCALAR'),
    -- 字典项
    ('sys_dict_item', '*',          NULL,       'SCALAR'),
    ('sys_dict_item', 'group_code', '所属分组', 'SCALAR'),
    ('sys_dict_item', 'code',       '字典项编码','SCALAR'),
    ('sys_dict_item', 'name',       '字典项名', 'SCALAR'),
    ('sys_dict_item', 'status',     '状态',     'ENUM:commonStatus'),
    ('sys_dict_item', 'sort_order', '排序',     'SCALAR'),
    ('sys_dict_item', 'ancestors',  NULL,       'EXCLUDE'),
    -- 通知模板
    ('sys_notification_template', '*',             NULL,     'SCALAR'),
    ('sys_notification_template', 'template_code', '模板编码', 'SCALAR'),
    ('sys_notification_template', 'name',          '模板名',  'SCALAR'),
    ('sys_notification_template', 'status',        '状态',    'SCALAR'),
    ('sys_notification_template', 'priority',      '优先级',  'SCALAR'),
    ('sys_notification_template', 'description',   '描述',    'SCALAR')
ON CONFLICT (table_name, column_name) DO NOTHING;


-- ============================================================================
-- 1) 系统预置字典
-- ============================================================================
INSERT INTO sys_dict_group (id, tenant_id, code, name, description, source, support_hierarchy)
VALUES (100001, 0, 'INDUSTRY',      '行业',     '租户企业所属行业（业务相关，由产品/UI 阶段补具体项）', 1, FALSE),
       (100002, 0, 'COMPANY_SIZE',  '公司规模', '企业人员规模，用于客户画像与统计',                       1, FALSE),
       (100003, 0, 'POSITION',      '岗位',     '成员岗位（业务相关，由租户/产品阶段补）',                1, FALSE),
       (100004, 0, 'GENDER',        '性别',     'GB/T 2261.1 标准 4 项映射；本系统采用三态简化',          1, FALSE),
       (100005, 0, 'EDUCATION',     '学历',     '从初中及以下到博士共 7 档',                              1, FALSE),
       (100006, 0, 'FILE_CATEGORY', '文件分类', '文件中心的分类（预留）',               1, FALSE)
ON CONFLICT (id) DO NOTHING;

-- COMPANY_SIZE（6 项）
INSERT INTO sys_dict_item (id, tenant_id, group_code, code, name, sort_order, source)
VALUES (200010, 0, 'COMPANY_SIZE', '1-49',       '1-49 人',       1, 1),
       (200011, 0, 'COMPANY_SIZE', '50-99',      '50-99 人',      2, 1),
       (200012, 0, 'COMPANY_SIZE', '100-499',    '100-499 人',    3, 1),
       (200013, 0, 'COMPANY_SIZE', '500-999',    '500-999 人',    4, 1),
       (200014, 0, 'COMPANY_SIZE', '1000-9999',  '1000-9999 人',  5, 1),
       (200015, 0, 'COMPANY_SIZE', '10000+',     '10000 人以上',  6, 1)
ON CONFLICT (id) DO NOTHING;

-- GENDER（3 项）
INSERT INTO sys_dict_item (id, tenant_id, group_code, code, name, sort_order, source)
VALUES (200020, 0, 'GENDER', 'MALE',    '男',   1, 1),
       (200021, 0, 'GENDER', 'FEMALE',  '女',   2, 1),
       (200022, 0, 'GENDER', 'UNKNOWN', '未知', 9, 1)
ON CONFLICT (id) DO NOTHING;

-- EDUCATION（7 项）
INSERT INTO sys_dict_item (id, tenant_id, group_code, code, name, sort_order, source)
VALUES (200030, 0, 'EDUCATION', 'JUNIOR',      '初中及以下', 1, 1),
       (200031, 0, 'EDUCATION', 'HIGH_SCHOOL', '高中',       2, 1),
       (200032, 0, 'EDUCATION', 'VOCATIONAL',  '中专',       3, 1),
       (200033, 0, 'EDUCATION', 'COLLEGE',     '大专',       4, 1),
       (200034, 0, 'EDUCATION', 'BACHELOR',    '本科',       5, 1),
       (200035, 0, 'EDUCATION', 'MASTER',      '硕士',       6, 1),
       (200036, 0, 'EDUCATION', 'DOCTOR',      '博士',       7, 1)
ON CONFLICT (id) DO NOTHING;

-- INDUSTRY（11 项；code 直接用中文，与租户档案存值一致）
INSERT INTO sys_dict_item (id, tenant_id, group_code, code, name, sort_order, source)
VALUES (200040, 0, 'INDUSTRY', '互联网', '互联网', 1,  1),
       (200041, 0, 'INDUSTRY', '制造业', '制造业', 2,  1),
       (200042, 0, 'INDUSTRY', '金融',   '金融',   3,  1),
       (200043, 0, 'INDUSTRY', '教育',   '教育',   4,  1),
       (200044, 0, 'INDUSTRY', '医疗',   '医疗',   5,  1),
       (200045, 0, 'INDUSTRY', '零售',   '零售',   6,  1),
       (200046, 0, 'INDUSTRY', '物流',   '物流',   7,  1),
       (200047, 0, 'INDUSTRY', '建筑',   '建筑',   8,  1),
       (200048, 0, 'INDUSTRY', '房地产', '房地产', 9,  1),
       (200049, 0, 'INDUSTRY', '农业',   '农业',   10, 1),
       (200050, 0, 'INDUSTRY', '其他',   '其他',   99, 1)
ON CONFLICT (id) DO NOTHING;


-- ============================================================================
-- 2) SYSTEM 基础套餐包
-- ============================================================================
INSERT INTO sys_package (id, code, name, description, is_system, status, default_ttl_days)
VALUES (100001, 'base', '基础包',
        '系统预置基础包：包含底座所有基础功能（多租户/权限/字典/部门/操作日志/任务中心/文件存储/通知/OpenAPI/Webhook 等）。所有租户开通即自动勾选、永久有效',
        TRUE, 1, NULL)
ON CONFLICT (id) DO NOTHING;


-- ============================================================================
-- 3) 初始运营账号
-- 初始凭据：username=admin@ops / password=admin123（首次部署后请立即改密）。
-- 邮箱列是确定性 AES 密文，迁移 SQL 不直接写；由应用启动时按
-- wen-saas-boot.ops.initial-admin-email 配置加密回填（InitialOpsAdminEmailSeeder）。
-- ============================================================================
INSERT INTO sys_user (id, tenant_id, username, password_hash, real_name, status, created_at, updated_at)
VALUES (1, 0, 'admin@ops',
        '$2y$10$J01MW9L3W7yoYOVULwvEZOv/wJ3qAn3QTTut1Ns7gI2QbI7qfLFy6',
        '运营端超管', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;


-- ============================================================================
-- 4) 运营预置角色 + 关联（2 个预置角色；工单处理由客户经理承接）
-- ============================================================================
INSERT INTO sys_role (id, tenant_id, code, name, description, status) VALUES
    (12100001, 0, 'OPS_SUPER_ADMIN', '超级管理员', '运营平台超管，拥有全部运营权限点；数据范围 = 全部',                                                          1),
    (12100002, 0, 'OPS_CSM',         '客户经理',   '负责客户续费/加购/暂停/启用/查看健康度/处理工单；数据范围 = 仅看自己归属（sys_tenant.csm_user_id=self）的租户', 1)
ON CONFLICT (id) DO NOTHING;

-- 运营角色默认数据范围（action=NULL 行；OPS_ALL=1 / OPS_OWNED=3）
INSERT INTO sys_role_data_scope (id, tenant_id, role_id, action, data_scope) VALUES
    (12100901, 0, 12100001, NULL, 1),
    (12100902, 0, 12100002, NULL, 3)
ON CONFLICT (id) DO NOTHING;

INSERT INTO sys_user_role (id, tenant_id, user_id, role_id) VALUES
    (12100101, 0, 1, 12100001)
ON CONFLICT (id) DO NOTHING;


-- ============================================================================
-- 5) 菜单树种子（sys_menu 表结构见 V1.0.0）
-- 种子后由运营「菜单管理」独占维护；permission_code 引用 PermissionRegistry 内存权威目录，
-- 启动期一致性校验兜底（悬空码置 dangling）。
-- ============================================================================

-- ============================== OPS（scope=1）==============================
INSERT INTO sys_menu (id, scope, node_key, parent_key, node_type, label, icon, path, permission_code, sort, status) VALUES
-- 顶层
(910001, 1, 'ops.workbench',  NULL,             2, '工作台',         'dashboard',           '/workbench',             'ops.dashboard.view',              0,  1),
(910002, 1, 'ops.tenants',    NULL,             2, '租户管理',       'team',                '/tenants',               'ops.tenant.list',                 10, 1),
(910003, 1, 'ops.profile',    NULL,             2, '个人中心',       'user',                '/profile',               NULL,                              20, 1),
(910010, 1, 'ops.g.packages', NULL,             1, '套餐与功能',     NULL,                  NULL,                     NULL,                              100,1),
(910011, 1, 'ops.g.ops',      NULL,             1, '运营',           NULL,                  NULL,                     NULL,                              200,1),
(910012, 1, 'ops.g.system',   NULL,             1, '系统',           NULL,                  NULL,                     NULL,                              300,1),
-- 租户管理动作
(910101, 1, 'ops.tenants.create',        'ops.tenants', 3, '开通',                 NULL, NULL, 'ops.tenant.create',         10, 1),
(910102, 1, 'ops.tenants.update',        'ops.tenants', 3, '编辑',                 NULL, NULL, 'ops.tenant.update',         20, 1),
(910103, 1, 'ops.tenants.impersonate',   'ops.tenants', 3, '代登录',               NULL, NULL, 'ops.tenant.impersonate',    30, 1),
(910104, 1, 'ops.tenants.suspend',       'ops.tenants', 3, '暂停/启用',            NULL, NULL, 'ops.tenant.suspend',        40, 1),
(910105, 1, 'ops.tenants.reset_pwd',     'ops.tenants', 3, '重置主管密码',         NULL, NULL, 'ops.tenant.reset_password', 50, 1),
(910106, 1, 'ops.tenants.contact_reveal','ops.tenants', 3, '查看成员完整联系方式', NULL, NULL, 'ops.tenant.contact.reveal', 60, 1),
-- 套餐与功能
(910020, 1, 'ops.modules',  'ops.g.packages', 2, '业务模块',     'appstore',            '/modules',  'ops.module.view',            10, 1),
(910021, 1, 'ops.plans',    'ops.g.packages', 2, '套餐包管理',   'book',                '/plans',    'ops.package.list',           20, 1),
(910022, 1, 'ops.recroles', 'ops.g.packages', 2, '推荐角色',     'safety-certificate',  '/recroles', 'ops.recommended_role.list',  30, 1),
(910120, 1, 'ops.plans.create',  'ops.plans', 3, '新建',     NULL, NULL, 'ops.package.create',  10, 1),
(910121, 1, 'ops.plans.update',  'ops.plans', 3, '编辑',     NULL, NULL, 'ops.package.update',  20, 1),
(910122, 1, 'ops.plans.publish', 'ops.plans', 3, '上下架',   NULL, NULL, 'ops.package.publish', 30, 1),
(910123, 1, 'ops.plans.archive', 'ops.plans', 3, '归档',     NULL, NULL, 'ops.package.archive', 40, 1),
(910124, 1, 'ops.plans.delete',  'ops.plans', 3, '删除',     NULL, NULL, 'ops.package.delete',  50, 1),
(910130, 1, 'ops.recroles.create',  'ops.recroles', 3, '新建',     NULL, NULL, 'ops.recommended_role.create',  10, 1),
(910131, 1, 'ops.recroles.update',  'ops.recroles', 3, '编辑',     NULL, NULL, 'ops.recommended_role.update',  20, 1),
(910132, 1, 'ops.recroles.publish', 'ops.recroles', 3, '发布/下架', NULL, NULL, 'ops.recommended_role.publish', 30, 1),
(910133, 1, 'ops.recroles.delete',  'ops.recroles', 3, '删除',     NULL, NULL, 'ops.recommended_role.delete',  40, 1),
(910134, 1, 'ops.recroles.copy',    'ops.recroles', 3, '复制',     NULL, NULL, 'ops.recommended_role.copy',    50, 1),
-- 运营
(910030, 1, 'ops.notif_tpl',  'ops.g.ops', 2, '通知模板',         'bell',         '/notification-templates', 'ops.notification_template.list', 10, 1),
(910031, 1, 'ops.announce',   'ops.g.ops', 2, '系统公告',         'notification', '/announce',               NULL,                             20, 0),
(910032, 1, 'ops.health',     'ops.g.ops', 2, '客户健康度',       'line-chart',   '/health',                 'ops.health.view',                30, 1),
(910033, 1, 'ops.tickets',    'ops.g.ops', 2, '工单管理',         'message',      '/tickets',                'ops.ticket.view',                40, 1),
(910034, 1, 'ops.compliance', 'ops.g.ops', 2, '数据隐私与合规',   'safety',       '/compliance',             'ops.compliance.view',            50, 1),
(910035, 1, 'ops.platform',   'ops.g.ops', 2, '平台监控',         'dashboard',    '/platform',               'ops.monitor.view',               60, 1),
(910140, 1, 'ops.notif_tpl.create',         'ops.notif_tpl', 3, '新建',     NULL, NULL, 'ops.notification_template.create',         10, 1),
(910141, 1, 'ops.notif_tpl.update',         'ops.notif_tpl', 3, '编辑',     NULL, NULL, 'ops.notification_template.update',         20, 1),
(910142, 1, 'ops.notif_tpl.enable_disable', 'ops.notif_tpl', 3, '启用/停用', NULL, NULL, 'ops.notification_template.enable_disable', 30, 1),
(910143, 1, 'ops.notif_tpl.delete',         'ops.notif_tpl', 3, '删除',     NULL, NULL, 'ops.notification_template.delete',         40, 1),
(910150, 1, 'ops.tickets.handle', 'ops.tickets', 3, '处理',     NULL, NULL, 'ops.ticket.handle', 10, 1),
(910151, 1, 'ops.tickets.reopen', 'ops.tickets', 3, '强制重启', NULL, NULL, 'ops.ticket.reopen', 20, 1),
-- 系统（GROUP + 5 子页）
(910040, 1, 'ops.sys.members', 'ops.g.system', 2, '成员管理', 'team',    '/sys/members',        'ops.team.member.list',   10, 1),
(910041, 1, 'ops.sys.roles',   'ops.g.system', 2, '运营角色', 'safety',  '/sys/roles',          'ops.team.role.list',     20, 1),
(910042, 1, 'ops.sys.params',  'ops.g.system', 2, '系统参数', 'setting', '/sys/params',         'ops.setting.view',       30, 1),
(910043, 1, 'ops.sys.oplog',   'ops.g.system', 2, '操作日志', 'profile', '/sys/operation-logs', 'ops.operation_log.list', 40, 1),
-- 菜单管理页自身也由菜单树驱动显隐；ops.menu.manage 由 OpsBusinessModuleDescriptor
-- 声明、OpsTeamRoleSeedRunner 启动期自动授予 OPS_SUPER_ADMIN。
(910044, 1, 'ops.sys.menus',   'ops.g.system', 2, '菜单管理', 'menu',    '/sys/menus',          'ops.menu.manage',        50, 1),
(910160, 1, 'ops.sys.members.create',         'ops.sys.members', 3, '新建',             NULL, NULL, 'ops.team.member.create',         10, 1),
(910161, 1, 'ops.sys.members.update',         'ops.sys.members', 3, '编辑',             NULL, NULL, 'ops.team.member.update',         20, 1),
(910162, 1, 'ops.sys.members.disable',        'ops.sys.members', 3, '停用/启用',        NULL, NULL, 'ops.team.member.disable',        30, 1),
(910163, 1, 'ops.sys.members.reset_pwd',      'ops.sys.members', 3, '重置密码',         NULL, NULL, 'ops.team.member.reset_password', 40, 1),
(910164, 1, 'ops.sys.members.unbind_oauth',   'ops.sys.members', 3, '解绑 OAuth',       NULL, NULL, 'ops.team.member.unbind_oauth',   50, 1),
(910165, 1, 'ops.sys.members.contact_reveal', 'ops.sys.members', 3, '查看完整联系方式', NULL, NULL, 'ops.team.member.contact.reveal', 60, 1),
(910170, 1, 'ops.sys.roles.create',           'ops.sys.roles', 3, '新建',     NULL, NULL, 'ops.team.role.create',            10, 1),
(910171, 1, 'ops.sys.roles.update',           'ops.sys.roles', 3, '编辑',     NULL, NULL, 'ops.team.role.update',            20, 1),
(910172, 1, 'ops.sys.roles.delete',           'ops.sys.roles', 3, '删除',     NULL, NULL, 'ops.team.role.delete',            30, 1),
(910173, 1, 'ops.sys.roles.assign_perm',      'ops.sys.roles', 3, '权限赋值', NULL, NULL, 'ops.team.role.assign_permission', 40, 1),
(910174, 1, 'ops.sys.roles.assign_member',    'ops.sys.roles', 3, '成员分配', NULL, NULL, 'ops.team.role.assign_member',     50, 1),
(910180, 1, 'ops.sys.params.update',          'ops.sys.params', 3, '修改',     NULL, NULL, 'ops.setting.update',       10, 1)
ON CONFLICT (id) DO NOTHING;

-- ============================== TENANT（scope=2）==============================
INSERT INTO sys_menu (id, scope, node_key, parent_key, node_type, label, icon, path, permission_code, sort, status) VALUES
-- 顶层
(920001, 2, 'tenant.workbench', NULL, 2, '工作台',   'home', '/workbench', NULL, 0, 1),
(920002, 2, 'tenant.profile',   NULL, 2, '个人中心', 'user', '/profile',   NULL, 1, 1),
(920010, 2, 'tenant.g.org',     NULL, 1, '组织与权限',   NULL, NULL, NULL, 100, 1),
(920011, 2, 'tenant.g.collab',  NULL, 1, '协作',         NULL, NULL, NULL, 200, 1),
(920012, 2, 'tenant.g.data',    NULL, 1, '数据任务',     NULL, NULL, NULL, 300, 1),
(920013, 2, 'tenant.g.system',  NULL, 1, '系统管理',     NULL, NULL, NULL, 400, 1),
(920014, 2, 'tenant.g.crm',     NULL, 1, '客户管理 Demo', NULL, NULL, NULL, 500, 1),
-- 组织与权限
(920020, 2, 'tenant.contacts', 'tenant.g.org', 2, '通讯录',     'apartment', '/contacts',      'org.dept.list', 10, 1),
(920021, 2, 'tenant.roles',    'tenant.g.org', 2, '角色与权限', 'team',      '/system/roles',  'role.list',     20, 1),
(920120, 2, 'tenant.contacts.dept_create',     'tenant.contacts', 3, '添加部门',           NULL, NULL, 'org.dept.create',          10, 1),
(920121, 2, 'tenant.contacts.dept_update',     'tenant.contacts', 3, '编辑部门',           NULL, NULL, 'org.dept.update',          20, 1),
(920122, 2, 'tenant.contacts.dept_delete',     'tenant.contacts', 3, '删除部门',           NULL, NULL, 'org.dept.delete',          30, 1),
(920123, 2, 'tenant.contacts.dept_leader',     'tenant.contacts', 3, '设置部门负责人',     NULL, NULL, 'org.dept.set_leader',      40, 1),
(920124, 2, 'tenant.contacts.member_list',     'tenant.contacts', 3, '查看成员',           NULL, NULL, 'org.member.list',          50, 1),
(920125, 2, 'tenant.contacts.member_create',   'tenant.contacts', 3, '添加成员',           NULL, NULL, 'org.member.create',        60, 1),
(920126, 2, 'tenant.contacts.member_update',   'tenant.contacts', 3, '编辑成员',           NULL, NULL, 'org.member.update',        70, 1),
(920127, 2, 'tenant.contacts.member_dept',     'tenant.contacts', 3, '管理所属部门',       NULL, NULL, 'org.member.assign_dept',   80, 1),
(920128, 2, 'tenant.contacts.member_status',   'tenant.contacts', 3, '停用/启用成员',      NULL, NULL, 'org.member.toggle_status', 90, 1),
(920129, 2, 'tenant.contacts.member_reset',    'tenant.contacts', 3, '重置成员密码',       NULL, NULL, 'org.member.reset_password',100,1),
(920130, 2, 'tenant.contacts.member_role',     'tenant.contacts', 3, '设置成员角色',       NULL, NULL, 'org.member.assign_role',   110,1),
(920131, 2, 'tenant.contacts.member_reveal',   'tenant.contacts', 3, '查看完整联系方式',   NULL, NULL, 'org.member.contact.reveal',120,1),
(920140, 2, 'tenant.roles.create', 'tenant.roles', 3, '新建角色', NULL, NULL, 'role.create', 10, 1),
(920141, 2, 'tenant.roles.update', 'tenant.roles', 3, '编辑角色', NULL, NULL, 'role.update', 20, 1),
(920142, 2, 'tenant.roles.delete', 'tenant.roles', 3, '删除角色', NULL, NULL, 'role.delete', 30, 1),
-- 协作
(920030, 2, 'tenant.messages', 'tenant.g.collab', 2, '消息中心', 'inbox',  '/messages',    NULL, 10, 1),
(920031, 2, 'tenant.tickets',  'tenant.g.collab', 2, '平台工单', 'message','/tickets',     NULL, 20, 1),
(920032, 2, 'tenant.files',    'tenant.g.collab', 2, '文件中心', 'folder', '/file-center', NULL, 30, 1),
(920150, 2, 'tenant.tickets.view_all', 'tenant.tickets', 3, '查看全部工单', NULL, NULL, 'base.ticket.view_all', 10, 1),
-- 数据任务
(920040, 2, 'tenant.tasks', 'tenant.g.data', 2, '导入导出', 'sync', '/tasks', NULL, 10, 1),
-- 系统管理
(920050, 2, 'tenant.dict',       'tenant.g.system', 2, '字典管理',       'book',        '/system/dictionaries',        'dict.group.list',                10, 1),
(920051, 2, 'tenant.company',    'tenant.g.system', 2, '公司信息',       'bank',        '/system/company',             'company.view',                   20, 1),
(920052, 2, 'tenant.account',    'tenant.g.system', 2, '套餐与账号',     'credit-card', '/system/account',             'account.view',                   30, 1),
(920053, 2, 'tenant.storage',    'tenant.g.system', 2, '存储设置',       'cloud',       '/system/storage',             'storage.view',                   40, 1),
(920054, 2, 'tenant.notif_tpl',  'tenant.g.system', 2, '通知模板',       'bell',        '/system/notification-templates','notification_template.view',    50, 1),
(920055, 2, 'tenant.security',   'tenant.g.system', 2, '安全合规',       'safety',      '/system/security',            'tenant.security.watermark.edit', 60, 1),
(920056, 2, 'tenant.audit',      'tenant.g.system', 2, '审计日志',       'audit',       '/system/audit-logs',          'audit.view',                     70, 1),
(920057, 2, 'tenant.fields',     'tenant.g.system', 2, '字段管理',       'appstore',    '/system/fields',              'custom_field.view',              80, 1),
(920058, 2, 'tenant.compliance', 'tenant.g.system', 2, '数据隐私与合规', 'safety',      '/system/compliance',          'base.compliance.config', 90, 1),
-- Webhook / 开放 API（权限码 webhook.* / api_key.* 在 BasePermissionContributor 注册）
(920059, 2, 'tenant.webhook',    'tenant.g.system', 2, 'Webhook 订阅',   'api',         '/system/webhooks',            'webhook.view',                   95, 1),
(920062, 2, 'tenant.apikey',     'tenant.g.system', 2, '开放 API',       'key',         '/system/api-keys',            'api_key.view',                   96, 1),
(920160, 2, 'tenant.dict.group_create', 'tenant.dict', 3, '新建字典分组', NULL, NULL, 'dict.group.create', 10, 1),
(920161, 2, 'tenant.dict.group_update', 'tenant.dict', 3, '编辑字典分组', NULL, NULL, 'dict.group.update', 20, 1),
(920162, 2, 'tenant.dict.group_delete', 'tenant.dict', 3, '删除字典分组', NULL, NULL, 'dict.group.delete', 30, 1),
(920163, 2, 'tenant.dict.item_list',    'tenant.dict', 3, '查看字典项',   NULL, NULL, 'dict.item.list',    40, 1),
(920164, 2, 'tenant.dict.item_create',  'tenant.dict', 3, '新建字典项',   NULL, NULL, 'dict.item.create',  50, 1),
(920165, 2, 'tenant.dict.item_update',  'tenant.dict', 3, '编辑字典项',   NULL, NULL, 'dict.item.update',  60, 1),
(920166, 2, 'tenant.dict.item_delete',  'tenant.dict', 3, '删除字典项',   NULL, NULL, 'dict.item.delete',  70, 1),
(920170, 2, 'tenant.company.edit',   'tenant.company',   3, '编辑公司信息', NULL, NULL, 'company.edit',              10, 1),
(920171, 2, 'tenant.storage.edit',   'tenant.storage',   3, '编辑存储设置', NULL, NULL, 'storage.edit',              10, 1),
(920172, 2, 'tenant.notif_tpl.edit', 'tenant.notif_tpl', 3, '编辑通知模板', NULL, NULL, 'notification_template.edit',10, 1),
(920173, 2, 'tenant.fields.manage',  'tenant.fields',    3, '管理字段',     NULL, NULL, 'custom_field.manage',       10, 1),
(920174, 2, 'tenant.compliance.config','tenant.compliance',3,'合规策略配置',NULL, NULL, 'base.compliance.config',    10, 1),
(920175, 2, 'tenant.webhook.manage', 'tenant.webhook',   3, '管理订阅',     NULL, NULL, 'webhook.manage',            10, 1),
(920181, 2, 'tenant.apikey.manage',  'tenant.apikey',    3, '管理凭证',     NULL, NULL, 'api_key.manage',            10, 1),
-- 客户管理 Demo（业务模块；正式态按套餐门禁）。「客户管理」绑管理视图权限点 view_manage：
-- 仅持有 crm_demo.customer.view 的成员侧栏只出现「我的客户」，管理入口只对持有 view_manage 的角色可见。
(920060, 2, 'tenant.crm.mine', 'tenant.g.crm', 2, '我的客户', 'user', '/crm-demo/my-customers', 'crm_demo.customer.view',        10, 1),
(920061, 2, 'tenant.crm.all',  'tenant.g.crm', 2, '客户管理', 'team', '/crm-demo/customers',    'crm_demo.customer.view_manage', 20, 1),
(920180, 2, 'tenant.crm.all.edit', 'tenant.crm.all', 3, '编辑客户', NULL, NULL, 'crm_demo.customer.edit', 10, 1)
ON CONFLICT (id) DO NOTHING;


-- ============================================================================
-- 6) 系统参数
-- ============================================================================
-- platform_info
INSERT INTO sys_setting (id, category, key, value, description) VALUES
    (12000101, 'platform_info', 'support_phone', '""'::JSONB, '客服联系电话（占位，待运营填）'),
    (12000102, 'platform_info', 'support_email', '""'::JSONB, '客服联系邮箱（占位，待运营填）'),
    (12000103, 'platform_info', 'announcement',  '""'::JSONB, '平台公告（P1）')
ON CONFLICT (id) DO NOTHING;

-- tenant_default
INSERT INTO sys_setting (id, category, key, value, description) VALUES
    (12000301, 'tenant_default', 'reset_token_ttl_hours', '48'::JSONB, '密码重置链接有效期（小时）'),
    (12000302, 'tenant_default', 'route_change_days',     '30'::JSONB, '路由位修改频次（天）')
ON CONFLICT (id) DO NOTHING;

-- security_policy
INSERT INTO sys_setting (id, category, key, value, description) VALUES
    (12000401, 'security_policy', 'password_min_length',  '8'::JSONB,    '密码最小长度'),
    (12000402, 'security_policy', 'login_max_attempts',   '5'::JSONB,    '登录失败锁定阈值（次）'),
    (12000403, 'security_policy', 'login_window_seconds', '600'::JSONB,  '锁定时长 / 滑动窗口（秒）'),
    (12000404, 'security_policy', 'session_ttl_seconds',  '7200'::JSONB, '登录态有效期（秒）：会话 / refresh token 默认时长，活跃时滑动续期；勾选"记住设备"走 30 天。JWT access token 时长另由 auth.access-token-ttl 控制'),
    (12000405, 'security_policy', 'upload_max_size_mb',   '100'::JSONB,  '单文件上传上限（MB）')
ON CONFLICT (id) DO NOTHING;

-- health_score
INSERT INTO sys_setting (id, category, key, value, description) VALUES
    (12600001, 'health_score', 'threshold_healthy',       '80'::JSONB, '健康(绿)评分下限'),
    (12600002, 'health_score', 'threshold_warning',       '50'::JSONB, '关注(黄)评分下限（低于此为危险红）'),
    (12600101, 'health_score', 'weight_no_login_30d',      '25'::JSONB, '长期无登录扣分'),
    (12600102, 'health_score', 'weight_mau_drop',          '10'::JSONB, '月活下降扣分'),
    (12600103, 'health_score', 'weight_expiring_soon',     '20'::JSONB, '距套餐到期临近扣分'),
    (12600104, 'health_score', 'weight_renewal_gap',       '10'::JSONB, '历史续费断档扣分（数据源待续费台账完善）'),
    (12600105, 'health_score', 'weight_urgent_ticket',     '15'::JSONB, '紧急工单未处理扣分'),
    (12600106, 'health_score', 'weight_ticket_volume',     '10'::JSONB, '工单数量异常扣分'),
    (12600107, 'health_score', 'weight_only_basic',        '10'::JSONB, '仅基础包扣分'),
    (12600201, 'health_score', 'no_login_days',            '30'::JSONB, '长期无登录判定天数'),
    (12600202, 'health_score', 'expiring_soon_days',       '30'::JSONB, '距到期临近判定天数'),
    (12600203, 'health_score', 'mau_drop_ratio_pct',       '70'::JSONB, '月活下降判定：当月月活 < 上月 * 此百分比'),
    (12600204, 'health_score', 'ticket_volume_threshold',  '5'::JSONB,  '工单数量异常判定：月工单数超过此值'),
    (12600205, 'health_score', 'mau_min_base',             '3'::JSONB,  '月活下降判定最小基数：上月活跃成员数低于此值不计「月活下降」，避免小样本误报')
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 7) 平台默认通知模板（tenant_id=0）
-- ============================================================================

-- 任务中心通用模板
INSERT INTO sys_notification_template
    (id, tenant_id, template_code, module_code, name, description,
     available_vars, default_channels, contents, notification_category, priority, status)
VALUES
    (125000001, 0, 'sys.task.completed', 'core', '任务执行完成',
     '通用任务执行完成时提醒发起人',
     '[{"name":"task_name","sampleValue":"批量导入客户","required":true},{"name":"finish_time","sampleValue":"2026-05-29 18:30","required":true}]'::jsonb,
     '["STATION"]'::jsonb,
     '{"STATION":{"title":"任务「{task_name}」已完成","body":"您的任务「{task_name}」已于 {finish_time} 执行完成，点击查看结果。"}}'::jsonb,
     'TODO_TASK', 'NORMAL', 'active'),

    (125000002, 0, 'sys.task.failed', 'core', '任务执行失败',
     '通用任务执行失败时提醒发起人',
     '[{"name":"task_name","sampleValue":"批量导入客户","required":true},{"name":"error_reason","sampleValue":"第 12 行手机号格式错误","required":true}]'::jsonb,
     '["STATION"]'::jsonb,
     '{"STATION":{"title":"任务「{task_name}」执行失败","body":"您的任务「{task_name}」执行失败：{error_reason}。请前往任务中心查看详情。"}}'::jsonb,
     'TODO_TASK', 'IMPORTANT', 'active'),

    (125000003, 0, 'sys.task.export.completed', 'core', '数据导出已完成',
     '业务数据导出任务完成后通知发起人（带任务中心下载指引）',
     '[{"name":"object_name","sampleValue":"成员列表","required":true},{"name":"rows","sampleValue":"1234","required":true},{"name":"finish_time","sampleValue":"2026-06-10 12:00","required":true}]'::jsonb,
     '["STATION"]'::jsonb,
     '{"STATION":{"title":"导出「{object_name}」已完成","body":"您发起的「{object_name}」导出已于 {finish_time} 完成，共 {rows} 条，请前往「导入导出」页下载（任务保留 30 天）。"}}'::jsonb,
     'TODO_TASK', 'NORMAL', 'active'),

    (125000004, 0, 'sys.task.approved', 'core', '审批通过',
     '审批流通过时提醒提交人',
     '[{"name":"task_name","sampleValue":"折扣申请","required":true},{"name":"approver","sampleValue":"高志强","required":true}]'::jsonb,
     '["STATION"]'::jsonb,
     '{"STATION":{"title":"「{task_name}」审批已通过","body":"您提交的「{task_name}」已由 {approver} 审批通过。"}}'::jsonb,
     'TODO_TASK', 'NORMAL', 'active'),

    (125000005, 0, 'sys.task.rejected', 'core', '审批拒绝',
     '审批流拒绝时提醒提交人，含拒绝原因',
     '[{"name":"task_name","sampleValue":"折扣申请","required":true},{"name":"approver","sampleValue":"高志强","required":true},{"name":"comment","sampleValue":"折扣超出权限范围","required":true}]'::jsonb,
     '["STATION"]'::jsonb,
     '{"STATION":{"title":"「{task_name}」审批被拒绝","body":"您提交的「{task_name}」被 {approver} 拒绝，原因：{comment}。"}}'::jsonb,
     'TODO_TASK', 'IMPORTANT', 'active'),

    (125000006, 0, 'sys.task.cancelled', 'core', '任务已取消',
     '任务被取消时提醒发起人',
     '[{"name":"task_name","sampleValue":"批量导入客户","required":true}]'::jsonb,
     '["STATION"]'::jsonb,
     '{"STATION":{"title":"任务「{task_name}」已取消","body":"您的任务「{task_name}」已被取消。"}}'::jsonb,
     'TODO_TASK', 'NORMAL', 'active')
ON CONFLICT DO NOTHING;

-- 危险租户每日摘要
INSERT INTO sys_notification_template
    (id, tenant_id, template_code, module_code, name, description,
     available_vars, default_channels, contents, notification_category, priority, status)
VALUES
    (126000001, 0, 'sys.health.danger_digest', 'ops', '危险租户每日摘要',
     '每天凌晨计算健康度后，向所有运营人员推送危险租户清单',
     '[{"name":"danger_count","sampleValue":"20","required":true},{"name":"danger_list","sampleValue":"· ABC 公司：30 天无登录、距到期 5 天","required":true}]'::jsonb,
     '["STATION"]'::jsonb,
     '{"STATION":{"title":"今日危险租户 {danger_count} 个","body":"以下租户健康度处于危险档，请及时跟进：\n{danger_list}"}}'::jsonb,
     'CUSTOMER_HEALTH', 'IMPORTANT', 'active')
ON CONFLICT DO NOTHING;

-- 工单流转模板
INSERT INTO sys_notification_template
    (id, tenant_id, template_code, module_code, name, description,
     available_vars, default_channels, contents, notification_category, priority, status)
VALUES
    (127000001, 0, 'base.ticket.submitted', 'base', '工单已提交',
     '用户提交工单后通知运营客服',
     '[{"name":"ticket_no","sampleValue":"T-20260530-0001","required":true},{"name":"title","sampleValue":"登录失败","required":true},{"name":"tenant_name","sampleValue":"ABC 公司","required":true}]'::jsonb,
     '["STATION","EMAIL"]'::jsonb,
     '{"STATION":{"title":"新工单 {ticket_no}","body":"{tenant_name} 提交了工单「{title}」（{ticket_no}），请及时认领处理。"},"EMAIL":{"title":"新工单待处理 {ticket_no}","body":"{tenant_name} 提交了工单「{title}」，工单号 {ticket_no}，请登录运营后台认领处理。"}}'::jsonb,
     'TICKET_ALERT', 'NORMAL', 'active'),

    (127000002, 0, 'base.ticket.assigned', 'base', '工单已认领',
     '客服认领工单后通知提交人',
     '[{"name":"ticket_no","sampleValue":"T-20260530-0001","required":true},{"name":"assignee_name","sampleValue":"客服小李","required":true}]'::jsonb,
     '["STATION"]'::jsonb,
     '{"STATION":{"title":"工单 {ticket_no} 已受理","body":"您的工单 {ticket_no} 已由 {assignee_name} 受理，正在处理中。"}}'::jsonb,
     NULL, 'NORMAL', 'active'),

    (127000003, 0, 'base.ticket.replied', 'base', '客服回复',
     '客服在工单中回复后通知提交人',
     '[{"name":"ticket_no","sampleValue":"T-20260530-0001","required":true},{"name":"assignee_name","sampleValue":"客服小李","required":true}]'::jsonb,
     '["STATION","EMAIL"]'::jsonb,
     '{"STATION":{"title":"工单 {ticket_no} 有新回复","body":"{assignee_name} 回复了您的工单 {ticket_no}，请查看。"},"EMAIL":{"title":"工单 {ticket_no} 有新回复","body":"{assignee_name} 回复了您的工单 {ticket_no}，请登录后台查看详情。"}}'::jsonb,
     NULL, 'NORMAL', 'active'),

    (127000004, 0, 'base.ticket.customer_replied', 'base', '用户回复',
     '提交人在工单中回复后通知处理人',
     '[{"name":"ticket_no","sampleValue":"T-20260530-0001","required":true},{"name":"submitter_name","sampleValue":"王经理","required":true}]'::jsonb,
     '["STATION"]'::jsonb,
     '{"STATION":{"title":"工单 {ticket_no} 用户有新回复","body":"{submitter_name} 回复了工单 {ticket_no}，请跟进。"}}'::jsonb,
     'TICKET_ALERT', 'NORMAL', 'active'),

    (127000005, 0, 'base.ticket.resolved', 'base', '工单已解决',
     '客服标记已解决后通知提交人确认',
     '[{"name":"ticket_no","sampleValue":"T-20260530-0001","required":true}]'::jsonb,
     '["STATION","EMAIL"]'::jsonb,
     '{"STATION":{"title":"工单 {ticket_no} 已解决","body":"您的工单 {ticket_no} 已标记为已解决，请确认；7 天内未回应将自动关闭。"},"EMAIL":{"title":"工单 {ticket_no} 已解决","body":"您的工单 {ticket_no} 已标记为已解决，请登录后台确认或重新打开；7 天内未回应将自动关闭。"}}'::jsonb,
     NULL, 'NORMAL', 'active'),

    (127000006, 0, 'base.ticket.sla_warning', 'base', 'SLA 临近提醒',
     '工单接近 SLA 截止（80%）时提醒处理人',
     '[{"name":"ticket_no","sampleValue":"T-20260530-0001","required":true},{"name":"sla_due_at","sampleValue":"2026-05-31 10:00","required":true}]'::jsonb,
     '["STATION"]'::jsonb,
     '{"STATION":{"title":"工单 {ticket_no} 即将超时","body":"工单 {ticket_no} 临近 SLA 截止（{sla_due_at}），请尽快处理。"}}'::jsonb,
     'TICKET_ALERT', 'IMPORTANT', 'active'),

    (127000007, 0, 'base.ticket.sla_breach', 'base', 'SLA 超时提醒',
     '工单超 SLA 后提醒处理人与客服主管',
     '[{"name":"ticket_no","sampleValue":"T-20260530-0001","required":true}]'::jsonb,
     '["STATION","EMAIL"]'::jsonb,
     '{"STATION":{"title":"工单 {ticket_no} 已超 SLA","body":"工单 {ticket_no} 已超过 SLA 截止时间，请立即处理。"},"EMAIL":{"title":"工单 {ticket_no} 已超 SLA","body":"工单 {ticket_no} 已超过 SLA 截止时间，请立即处理。"}}'::jsonb,
     'TICKET_ALERT', 'IMPORTANT', 'active'),

    (127000008, 0, 'base.ticket.auto_closed', 'base', '工单自动关闭',
     '工单已解决 7 天未回应自动关闭后通知提交人',
     '[{"name":"ticket_no","sampleValue":"T-20260530-0001","required":true}]'::jsonb,
     '["STATION"]'::jsonb,
     '{"STATION":{"title":"工单 {ticket_no} 已关闭","body":"您的工单 {ticket_no} 因已解决后 7 天未回应，已自动关闭。如仍有问题可重新打开。"}}'::jsonb,
     NULL, 'NORMAL', 'active')
ON CONFLICT DO NOTHING;

-- 超量导出审批模板
INSERT INTO sys_notification_template
    (id, tenant_id, template_code, module_code, name, description,
     available_vars, default_channels, contents, notification_category, priority, status)
VALUES
    (128000001, 0, 'base.compliance.export.pending_approval', 'base', '超量导出待审批',
     '用户发起的导出超过阈值，转审批后通知审批人',
     '[{"name":"request_no","sampleValue":"EX-20260531-0001","required":true},{"name":"object_type","sampleValue":"客户列表","required":true},{"name":"applicant_name","sampleValue":"王经理","required":true},{"name":"estimated_count","sampleValue":"52000","required":true}]'::jsonb,
     '["STATION","EMAIL"]'::jsonb,
     '{"STATION":{"title":"超量导出待审批 {request_no}","body":"{applicant_name} 申请导出「{object_type}」约 {estimated_count} 条，已超阈值转审批，请尽快处理。"},"EMAIL":{"title":"超量导出待审批 {request_no}","body":"{applicant_name} 申请导出「{object_type}」约 {estimated_count} 条，已超阈值转审批，请登录后台审批。"}}'::jsonb,
     NULL, 'IMPORTANT', 'active'),

    (128000002, 0, 'base.compliance.export.approved', 'base', '超量导出已批准',
     '审批人批准超量导出后通知申请人',
     '[{"name":"request_no","sampleValue":"EX-20260531-0001","required":true},{"name":"object_type","sampleValue":"客户列表","required":true}]'::jsonb,
     '["STATION"]'::jsonb,
     '{"STATION":{"title":"导出申请 {request_no} 已批准","body":"您的导出申请「{object_type}」（{request_no}）已批准，任务将后台执行，完成后可在任务中心下载。"}}'::jsonb,
     NULL, 'NORMAL', 'active'),

    (128000003, 0, 'base.compliance.export.rejected', 'base', '超量导出已拒绝',
     '审批人拒绝超量导出后通知申请人',
     '[{"name":"request_no","sampleValue":"EX-20260531-0001","required":true},{"name":"object_type","sampleValue":"客户列表","required":true}]'::jsonb,
     '["STATION"]'::jsonb,
     '{"STATION":{"title":"导出申请 {request_no} 已拒绝","body":"您的导出申请「{object_type}」（{request_no}）已被拒绝，如有疑问请联系审批人。"}}'::jsonb,
     NULL, 'NORMAL', 'active')
ON CONFLICT DO NOTHING;

-- 全量数据导出完成模板
INSERT INTO sys_notification_template
    (id, tenant_id, template_code, module_code, name, description,
     available_vars, default_channels, contents, notification_category, priority, status)
VALUES
    (129000001, 0, 'base.compliance.data_export.completed', 'base', '全量数据导出已完成',
     '主管理员发起的全量数据导出打包完成后通知本人',
     '[{"name":"request_no","sampleValue":"DX-20260531-0001","required":true},{"name":"expires_days","sampleValue":"7","required":true}]'::jsonb,
     '["STATION","EMAIL"]'::jsonb,
     '{"STATION":{"title":"全量数据导出 {request_no} 已完成","body":"您发起的全量数据导出（{request_no}）已打包完成，请在「数据隐私与合规 → 全量数据导出」下载，链接 {expires_days} 天内有效。"},"EMAIL":{"title":"全量数据导出 {request_no} 已完成","body":"您发起的全量数据导出（{request_no}）已打包完成，请登录后台下载，链接 {expires_days} 天内有效。"}}'::jsonb,
     NULL, 'IMPORTANT', 'active')
ON CONFLICT DO NOTHING;

-- Webhook 连续失败自动停用的管理员告警模板。
-- notification_category 留空 = 不参与用户通知偏好过滤，确保安全/运维类提醒必达。
INSERT INTO sys_notification_template
    (id, tenant_id, template_code, module_code, name, description,
     available_vars, default_channels, contents, notification_category, priority, status)
VALUES
    (130000001, 0, 'sys.webhook.auto_disabled', 'core', 'Webhook 自动停用告警',
     'Webhook 订阅连续投递失败达到阈值被自动停用时告警租户主管理员',
     '[{"name":"webhookName","sampleValue":"ERP 同步回调","required":true},{"name":"failures","sampleValue":"10","required":true}]'::jsonb,
     '["STATION"]'::jsonb,
     '{"STATION":{"title":"Webhook 订阅「{webhookName}」已自动停用","body":"订阅「{webhookName}」连续 {failures} 个事件投递失败，已被自动停用。请检查回调端点后在「系统管理 → Webhook 订阅」重新启用。"}}'::jsonb,
     NULL, 'IMPORTANT', 'active')
ON CONFLICT DO NOTHING;

-- 运营代登录知情提醒：运营发起代登录进入租户后台时，向该租户主管理员发系统站内信。
-- notification_category 留空 = 不参与用户通知偏好过滤，确保安全提醒必达。
INSERT INTO sys_notification_template
    (id, tenant_id, template_code, module_code, name, description,
     available_vars, default_channels, contents, notification_category, priority, status)
VALUES
    (145000001, 0, 'sys.security.impersonate_started', 'core', '运营代登录提醒',
     '运营人员发起代登录进入租户后台时，通知该租户主管理员',
     '[{"name":"operator_name","sampleValue":"平台超级管理员","required":true},{"name":"start_time","sampleValue":"2026-06-15 10:30","required":true}]'::jsonb,
     '["STATION"]'::jsonb,
     '{"STATION":{"title":"运营人员已进入你的租户后台","body":"运营人员「{operator_name}」于 {start_time} 通过平台授权代登录进入了你的租户后台进行协助操作。其间所有操作均记录于审计日志。如对此操作有疑问，请联系平台。"}}'::jsonb,
     NULL, 'IMPORTANT', 'active')
ON CONFLICT DO NOTHING;


-- ============================================================================
-- 初始化数据到此结束。审计函数与触发器见 V1.0.0__init_schema.sql。
-- ============================================================================
