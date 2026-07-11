import { moduleRegistry } from './modules';

/**
 * 模块 code → 用户可读展示名。
 *
 * <p>非业务模块的系统域（core / member / audit …）在此表内维护；业务模块（crm_demo 等）
 * 回退 moduleRegistry 清单里的 name，新增业务模块无需改这里。两边都查不到时回退原始 code，
 * 保证界面不因缺映射而空白。
 */
const BASE_DOMAIN_LABEL: Record<string, string> = {
  core: '系统通用',
  sys: '系统',
  system: '系统设置',
  member: '成员管理',
  audit: '操作日志',
  ops: '平台运营',
  base: '底座',
};

export function moduleDisplayName(code: string | null | undefined): string {
  const c = code?.trim();
  if (!c) return '系统';
  return BASE_DOMAIN_LABEL[c] ?? moduleRegistry.byCode.get(c)?.name ?? c;
}
