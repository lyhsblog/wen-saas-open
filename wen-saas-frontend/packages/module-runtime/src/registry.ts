import type { BusinessModuleManifest, RouteContribution } from './manifest';

/** 路由经 registry 处理后的最终形态。 */
export type ResolvedRoute = {
  /** 标准化后的路由路径（首位 `/`）。manifest 作者完全掌控，不强加模块前缀。 */
  fullPath: string;
  moduleCode: string;
  contribution: RouteContribution;
};

export type ModuleRegistry = {
  /** topo 排序后的模块列表（dependsOn 在前）。 */
  modules: BusinessModuleManifest[];
  byCode: Map<string, BusinessModuleManifest>;
  /**
   * 全量 tenantRoutes（不按 caps 过滤）。供构建期把路由表注入 react-router；
   * 运行时的权限/模块开关由 PermissionGate / PackageGate 在路由 element 侧拦截。
   */
  getTenantRoutes(): ResolvedRoute[];
  /** 同上，给 H5 端。 */
  getH5Routes(): ResolvedRoute[];
};

function topoSort(manifests: BusinessModuleManifest[]): BusinessModuleManifest[] {
  const byCode = new Map(manifests.map((m) => [m.code, m]));
  const result: BusinessModuleManifest[] = [];
  const visited = new Set<string>();
  const visiting = new Set<string>();

  function visit(code: string, chain: string[]): void {
    if (visited.has(code)) return;
    if (visiting.has(code)) {
      throw new Error(`module dependency cycle: ${[...chain, code].join(' -> ')}`);
    }
    const m = byCode.get(code);
    if (!m) throw new Error(`module dependency missing: ${code}`);
    visiting.add(code);
    for (const dep of m.dependsOn ?? []) {
      visit(dep, [...chain, code]);
    }
    visiting.delete(code);
    visited.add(code);
    result.push(m);
  }

  for (const m of manifests) visit(m.code, []);
  return result;
}

/** path 规范化：保证以 `/` 开头，去掉末尾 `/`（根路径除外）。 */
function normalizePath(path: string): string {
  const withSlash = path.startsWith('/') ? path : `/${path}`;
  if (withSlash.length > 1 && withSlash.endsWith('/')) return withSlash.slice(0, -1);
  return withSlash;
}

export function registerModules(manifests: BusinessModuleManifest[]): ModuleRegistry {
  const codes = new Set<string>();
  for (const m of manifests) {
    if (codes.has(m.code)) {
      throw new Error(`duplicate module code: ${m.code}`);
    }
    codes.add(m.code);
  }
  const ordered = topoSort(manifests);
  const byCode = new Map(ordered.map((m) => [m.code, m]));

  return {
    modules: ordered,
    byCode,
    getTenantRoutes() {
      const out: ResolvedRoute[] = [];
      for (const m of ordered) {
        for (const route of m.tenantRoutes ?? []) {
          out.push({
            fullPath: normalizePath(route.path),
            moduleCode: m.code,
            contribution: route,
          });
        }
      }
      return out;
    },
    getH5Routes() {
      const out: ResolvedRoute[] = [];
      for (const m of ordered) {
        for (const route of m.h5Routes ?? []) {
          out.push({
            fullPath: normalizePath(route.path),
            moduleCode: m.code,
            contribution: route,
          });
        }
      }
      return out;
    },
  };
}
