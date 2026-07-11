import { registerModules, type BusinessModuleManifest } from '@wen-saas/module-runtime';
import { baseManifest } from '@wen-saas/base-module';

/**
 * tenant-web 启用的业务模块清单。按依赖顺序加，registry 自动 topo 排序。
 *
 * <p>本清单现只承担**前端路由登记**（`tenantRoutes`：path → 页面组件）与**模块身份/套餐门控**
 * （`code`/`alwaysOn`）。菜单结构与权限点均不在此声明——
 * 导航树 = 后端 `capabilities.menus`（sys_menu 派生），权限点 = 后端 `PermissionRegistry`，
 * 角色目录 = `catalogTree`。故这里不再写 `tenantMenus`/`permissionPoints`：
 * 它们曾喂 `moduleRegistry.resolveTenantMenus`（旧硬编码导航 / 工作台入口），现两者均已改吃后端，
 * 留着只会造成「改这里以为能改菜单」的误导。
 */
const tenantBaseManifest: BusinessModuleManifest = {
  ...baseManifest,
  // 底座框架模块：工作台 / 字典 / 角色 / 通讯录对所有租户恒可见，不按套餐门禁。
  alwaysOn: true,
  tenantRoutes: [
    {
      path: '/workbench',
      loader: () =>
        import('../features/workbench/WorkbenchPage').then((m) => ({ default: m.WorkbenchPage })),
    },
    {
      path: '/contacts',
      loader: () => import('../features/dept/DeptPage').then((m) => ({ default: m.DeptPage })),
      permission: 'org.dept.list',
    },
    {
      path: '/system/dictionaries',
      loader: () => import('../features/dict/DictPage').then((m) => ({ default: m.DictPage })),
      permission: 'dict.group.list',
    },
    {
      path: '/system/roles',
      loader: () => import('../features/role/RolePage').then((m) => ({ default: m.RolePage })),
      permission: 'role.list',
    },
    {
      // 个人中心：任意已登录成员自助管理自己的账号，无权限门禁，经顶部用户块进入（不挂侧栏菜单）。
      path: '/profile',
      loader: () =>
        import('../features/profile/ProfilePage').then((m) => ({ default: m.ProfilePage })),
    },
  ],
};

/**
 * 平台工单。通用客服工具，非套餐门禁项（后端 apiPathPrefixes 为空）。
 * 命名特意带「平台」以与未来业务模块内的「业务工单」区分。
 */
const ticketManifest: BusinessModuleManifest = {
  code: 'ticket',
  name: '平台工单',
  version: '0.0.1',
  description: '租户向平台客服提交工单、查看进度、沟通。',
  alwaysOn: true,
  tenantRoutes: [
    {
      path: '/tickets',
      loader: () =>
        import('../features/ticket/TicketPage').then((m) => ({ default: m.TicketPage })),
    },
  ],
};

/**
 * 数据隐私与合规。横切底座能力、非套餐门禁；页面按 base.compliance.config 门禁（策略配置权）——
 * 审批人（export.approve，如部门主管）的操作入口在任务中心「待审批」，不需要进本页。
 */
const complianceManifest: BusinessModuleManifest = {
  code: 'compliance',
  name: '数据隐私与合规',
  version: '0.0.1',
  description: '导出审批等横切合规能力。',
  alwaysOn: true,
  tenantRoutes: [
    {
      path: '/system/compliance',
      loader: () =>
        import('../features/compliance/CompliancePage').then((m) => ({
          default: m.CompliancePage,
        })),
      permission: 'base.compliance.config',
    },
  ],
};

/**
 * 文件中心。通用底座能力，非套餐门禁。
 */
const fileCenterManifest: BusinessModuleManifest = {
  code: 'fileCenter',
  name: '文件中心',
  version: '0.0.1',
  description: '租户文件存储与管理。',
  alwaysOn: true,
  tenantRoutes: [
    {
      path: '/file-center',
      loader: () =>
        import('../features/fileCenter/FileCenterPage').then((m) => ({
          default: m.FileCenterPage,
        })),
    },
    {
      // 链接分享访问页（P0 仅限本租户成员登录后访问）。
      path: '/file-center/share/:shareToken',
      loader: () =>
        import('../features/fileCenter/SharedFilePage').then((m) => ({
          default: m.SharedFilePage,
        })),
    },
  ],
};

/**
 * 消息中心。站内信收件箱，全体成员可见自己的消息；顶部铃铛接未读数。
 */
const messageInboxManifest: BusinessModuleManifest = {
  code: 'messageInbox',
  name: '消息中心',
  version: '0.0.1',
  description: '租户站内信收件箱。',
  alwaysOn: true,
  tenantRoutes: [
    {
      path: '/messages',
      loader: () =>
        import('../features/messageInbox/MessageInboxPage').then((m) => ({
          default: m.MessageInboxPage,
        })),
    },
  ],
};

/**
 * 系统管理 · 公司信息 / 套餐与账号 / 存储设置。租户自助底座能力，非套餐门禁；
 * 各页按各自 *.view 门禁（主管理员恒可见）。
 */
const tenantSelfManifest: BusinessModuleManifest = {
  code: 'tenantSelf',
  name: '租户自助设置',
  version: '0.0.1',
  description: '公司信息 / 套餐与账号 / 存储设置。',
  alwaysOn: true,
  tenantRoutes: [
    {
      path: '/system/company',
      loader: () =>
        import('../features/company/CompanyPage').then((m) => ({ default: m.CompanyPage })),
      permission: 'company.view',
    },
    {
      path: '/system/account',
      loader: () =>
        import('../features/account/AccountPage').then((m) => ({ default: m.AccountPage })),
      permission: 'account.view',
    },
    {
      path: '/system/storage',
      loader: () =>
        import('../features/storage/StoragePage').then((m) => ({ default: m.StoragePage })),
      permission: 'storage.view',
    },
    {
      path: '/system/notification-templates',
      loader: () =>
        import('../features/notificationTemplate/NotificationTemplatePage').then((m) => ({
          default: m.NotificationTemplatePage,
        })),
      permission: 'notification_template.view',
    },
    {
      path: '/system/security',
      loader: () =>
        import('../features/security/SecurityPage').then((m) => ({ default: m.SecurityPage })),
      permission: 'tenant.security.watermark.edit',
    },
    {
      path: '/system/webhooks',
      loader: () =>
        import('../features/webhook/WebhookPage').then((m) => ({ default: m.WebhookPage })),
      permission: 'webhook.view',
    },
    {
      path: '/system/api-keys',
      loader: () =>
        import('../features/apikey/ApiKeyPage').then((m) => ({ default: m.ApiKeyPage })),
      permission: 'api_key.view',
    },
  ],
};

/**
 * 任务中心。导入 / 导出异步任务统一管理，对全体成员开放，非套餐门禁。
 */
const taskCenterManifest: BusinessModuleManifest = {
  code: 'taskCenter',
  name: '导入导出',
  version: '0.0.1',
  description: '导入 / 导出任务统一管理。',
  alwaysOn: true,
  tenantRoutes: [
    {
      path: '/tasks',
      loader: () =>
        import('../features/taskCenter/TaskCenterPage').then((m) => ({
          default: m.TaskCenterPage,
        })),
    },
  ],
};

/**
 * 审计日志（UX3）。横切底座能力、非套餐门禁：审计日志按 audit.view 门禁；我的操作记录任意成员看自己。
 */
const auditManifest: BusinessModuleManifest = {
  code: 'audit',
  name: '审计日志',
  version: '0.0.1',
  description: '租户操作审计日志 + 我的操作记录（UX3）。',
  alwaysOn: true,
  tenantRoutes: [
    {
      path: '/system/audit-logs',
      loader: () =>
        import('../features/audit/AuditLogPage').then((m) => ({ default: m.AuditLogPage })),
      permission: 'audit.view',
    },
    {
      // 我的操作记录：任意成员看自己，无权限门禁，经顶部用户卡进入（不挂侧栏菜单）。
      path: '/operation-logs',
      loader: () =>
        import('../features/audit/MyOperationLogPage').then((m) => ({
          default: m.MyOperationLogPage,
        })),
    },
  ],
};

/**
 * 字段管理。底座横切能力，挂系统管理下，按 custom_field.view 门禁（主管理员恒可）。
 */
const customFieldManifest: BusinessModuleManifest = {
  code: 'custom_field',
  name: '字段管理',
  version: '0.0.1',
  description: '自定义字段统一管理。',
  alwaysOn: true,
  tenantRoutes: [
    {
      path: '/system/fields',
      loader: () =>
        import('../features/customfield/CustomFieldPage').then((m) => ({
          default: m.CustomFieldPage,
        })),
      permission: 'custom_field.view',
    },
  ],
};

/**
 * 客户管理 Demo。自定义字段 / 列偏好 / 数据权限三能力的样板业务模块。
 * 套餐门禁项（alwaysOn:false）：仅授予 crm_demo 套餐的租户可见菜单 / 工作台入口 / 注册前端路由；
 * 未购租户即便是主管理员也不可见（与后端 capabilities.menus + PackageStatusWebFilter 真拦截一致）。
 */
const crmDemoManifest: BusinessModuleManifest = {
  code: 'crm_demo',
  name: '客户管理 Demo',
  version: '0.0.1',
  description: '自定义字段 / 列偏好 / 数据权限三能力样板。',
  alwaysOn: false,
  tenantRoutes: [
    {
      path: '/crm-demo/my-customers',
      loader: () =>
        import('../features/crmdemo/MyCustomersPage').then((m) => ({ default: m.MyCustomersPage })),
      permission: 'crm_demo.customer.view',
    },
    {
      path: '/crm-demo/customers',
      loader: () =>
        import('../features/crmdemo/CustomersPage').then((m) => ({ default: m.CustomersPage })),
      permission: 'crm_demo.customer.view_manage',
    },
  ],
};

export const moduleRegistry = registerModules([
  tenantBaseManifest,
  auditManifest,
  ticketManifest,
  complianceManifest,
  fileCenterManifest,
  messageInboxManifest,
  taskCenterManifest,
  tenantSelfManifest,
  customFieldManifest,
  crmDemoManifest,
]);
