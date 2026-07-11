/**
 * 平台「底座基础能力」清单（O7-01/T9-01）：所有套餐默认包含、与可选「业务模块」(crm_demo 等)
 * 区分。套餐概览 / 租户套餐页以只读分组展示，避免「0 项 / 未包含任何模块」误导租户。
 *
 * 这些是平台框架级能力，不作为套餐数据建模（后端 moduleCodes 仅含业务模块），故前端静态维护。
 */
export type BaseCapability = { name: string; desc: string };

export const PLATFORM_BASE_CAPABILITIES: BaseCapability[] = [
  { name: '多租户隔离', desc: '租户数据隔离与独立配置' },
  { name: '权限与角色', desc: 'RBAC 角色 / 权限点 / 数据范围' },
  { name: '组织与成员', desc: '部门、成员、岗位管理' },
  { name: '数据字典', desc: '业务字典维护与下发' },
  { name: '操作日志与审计', desc: '操作留痕 / 登录日志' },
  { name: '任务中心', desc: '异步导入 / 导出任务' },
  { name: '文件存储', desc: '文件中心与签名下载' },
  { name: '消息通知', desc: '站内信 / 邮件 / 短信通道' },
  { name: 'OpenAPI', desc: '开放接口与凭据管理' },
  { name: 'Webhook', desc: '事件回调订阅' },
];
