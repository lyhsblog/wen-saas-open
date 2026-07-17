import { create } from 'zustand';
import type { HttpClient } from '@wen-saas/api-client';

/**
 * 导航菜单节点（来自后端 `sys_menu` 解析树）。GROUP 节点 path 为 null（仅作分组标题），
 * PAGE 节点带 path；后端已按当前用户权限过滤可见性 + 排序，前端直接渲染。
 */
export type MenuNode = {
  path: string | null;
  name: string;
  icon: string | null;
  sort: number;
  children: MenuNode[];
};

/**
 * 当前用户的能力包。契约未 freeze，后端仍可能调整：
 * 暂按 `GET /api/v1/me/capabilities` 实现 loadCapabilities；
 * 后端最终若改为由 /api/auth/me 一并返回，调用方改 loader 即可。
 */
export type Capabilities = {
  /** 权限点列表，如 `org.member.create`、`dict.group.delete`。 */
  permissions: string[];
  /** 已启用业务模块编码列表。 */
  enabledModules: string[];
  /** 当前生效套餐编码列表。 */
  activePackages: string[];
  /** 套餐到期 / 欠费 → 全局只读模式。 */
  packageReadonly: boolean;
  /** 是否租户管理员（拥有全部权限）。 */
  tenantOwner: boolean;
  /** 当前会话是否为运营代登录态。代登录态下展示全局水印 + 标识条。 */
  impersonating: boolean;
  /** 代登录态下发起代登录的运营操作人登录名；非代登录态为 null。供水印 / 标识溯源展示。 */
  operatorName: string | null;
  /** 当前用户真实姓名（未加密）；供防截屏水印展示，可能为空串。 */
  realName: string;
  /** 防截屏水印是否开启（租户级开关，默认开启）；为 true 时全页平铺可溯源水印。 */
  screenshotWatermark: boolean;
  /** 导航菜单树（后端 `sys_menu` 按当前用户权限解析后的可见树）。 */
  menus: MenuNode[];
};

export const emptyCapabilities: Capabilities = {
  permissions: [],
  enabledModules: [],
  activePackages: [],
  packageReadonly: false,
  tenantOwner: false,
  impersonating: false,
  operatorName: null,
  realName: '',
  screenshotWatermark: true,
  menus: [],
};

type Store = {
  caps: Capabilities | null;
  loading: boolean;
  error: unknown;
  setCapabilities: (caps: Capabilities | null) => void;
  setLoading: (loading: boolean) => void;
  setError: (error: unknown) => void;
  reset: () => void;
};

export const useCapabilitiesStore = create<Store>((set) => ({
  caps: null,
  loading: false,
  error: null,
  setCapabilities: (caps) => set({ caps, error: null }),
  setLoading: (loading) => set({ loading }),
  setError: (error) => set({ error, loading: false }),
  reset: () => set({ caps: null, loading: false, error: null }),
}));

/**
 * 从后端拉一次 capabilities。建议在登录成功 & 应用启动两处调用。
 * 失败后调用方按 `useCapabilitiesError()` 取错并展示，本函数不再 throw 已被 catch 的网络错。
 */
/** 后端返回的模块 / 套餐视图（`enabledModules` 是 ModuleView[]、`activePackages` 是 PackageView[]）。 */
type CodeView = { code: string; name?: string; version?: string; readonly?: boolean };

/** 后端 capabilities 原始响应：模块 / 套餐是对象数组，本地扁平化成 code 列表后再进 store。 */
type RawCapabilities = Omit<Capabilities, 'enabledModules' | 'activePackages'> & {
  enabledModules?: CodeView[] | string[];
  activePackages?: CodeView[] | string[];
};

/** 把 code 视图数组扁平成 code 字符串数组；兼容后端偶发直接给字符串。 */
function toCodes(list: CodeView[] | string[] | undefined): string[] {
  return (list ?? []).map((m) => (typeof m === 'string' ? m : m.code));
}

export async function loadCapabilities(http: HttpClient): Promise<Capabilities> {
  const store = useCapabilitiesStore.getState();
  store.setLoading(true);
  try {
    const res = await http.get<RawCapabilities>('/api/v1/me/capabilities');
    const caps: Capabilities = {
      permissions: res.data.permissions ?? [],
      enabledModules: toCodes(res.data.enabledModules),
      activePackages: toCodes(res.data.activePackages),
      packageReadonly: res.data.packageReadonly ?? false,
      tenantOwner: res.data.tenantOwner ?? false,
      impersonating: res.data.impersonating ?? false,
      operatorName: res.data.operatorName ?? null,
      realName: res.data.realName ?? '',
      screenshotWatermark: res.data.screenshotWatermark ?? true,
      menus: res.data.menus ?? [],
    };
    store.setCapabilities(caps);
    store.setLoading(false);
    return caps;
  } catch (err) {
    store.setError(err);
    throw err;
  }
}

/* ============ hooks ============ */

export function useCapabilities(): Capabilities | null {
  return useCapabilitiesStore((s) => s.caps);
}

export function useCapabilitiesLoading(): boolean {
  return useCapabilitiesStore((s) => s.loading);
}

export function useCapabilitiesError(): unknown {
  return useCapabilitiesStore((s) => s.error);
}

/**
 * 是否拥有权限点。租户管理员恒为 true；capabilities 未加载时返回 false。
 * 若需要"还没决定 / 加载中"的态，请配合 `useCapabilities() == null` 自行判别。
 */
export function useHasPermission(permission: string | undefined): boolean {
  return useCapabilitiesStore((s) => {
    if (!s.caps) return false;
    if (s.caps.tenantOwner) return true;
    if (!permission) return true;
    return s.caps.permissions.includes(permission);
  });
}

/** 模块是否启用。未加载时返回 false。 */
export function useHasModule(module: string | undefined): boolean {
  return useCapabilitiesStore((s) => {
    if (!s.caps) return false;
    if (!module) return true;
    return s.caps.enabledModules.includes(module);
  });
}

/** 导航菜单树（后端 sys_menu 解析）。未加载时返回空数组。 */
export function useMenus(): MenuNode[] {
  return useCapabilitiesStore((s) => s.caps?.menus ?? EMPTY_MENUS);
}

const EMPTY_MENUS: MenuNode[] = [];

/** 套餐到期只读模式。 */
export function useIsReadonly(): boolean {
  return useCapabilitiesStore((s) => s.caps?.packageReadonly ?? false);
}

/** 运营代登录态信息。`impersonating` 为 true 时展示全局水印 + 标识条；`operatorName` 供溯源展示。
 *  用两个原子 selector 返回原始值，避免每次返回新对象引起的重渲染循环。 */
export function useImpersonation(): { impersonating: boolean; operatorName: string | null } {
  const impersonating = useCapabilitiesStore((s) => s.caps?.impersonating ?? false);
  const operatorName = useCapabilitiesStore((s) => s.caps?.operatorName ?? null);
  return { impersonating, operatorName };
}

/** 防截屏水印是否开启（租户级开关，默认开启）+ 当前用户真实姓名（水印内容用）。
 *  用原子 selector 返回原始值，避免每次返回新对象引起的重渲染循环。 */
export function useScreenshotWatermark(): { enabled: boolean; realName: string } {
  const enabled = useCapabilitiesStore((s) => s.caps?.screenshotWatermark ?? false);
  const realName = useCapabilitiesStore((s) => s.caps?.realName ?? '');
  return { enabled, realName };
}
