import { describe, expect, it } from 'vitest';
import type { BusinessModuleManifest } from './manifest';
import { registerModules } from './registry';

const depA: BusinessModuleManifest = { code: 'a', name: 'A', version: '1' };
const depB: BusinessModuleManifest = { code: 'b', name: 'B', version: '1', dependsOn: ['a'] };

describe('registerModules', () => {
  it('重复 code 抛错', () => {
    expect(() => registerModules([depA, { ...depA, name: '其它' }])).toThrow(
      /duplicate module code: a/,
    );
  });

  it('dependsOn 缺失抛错', () => {
    const orphan: BusinessModuleManifest = { ...depB, dependsOn: ['nonexistent'] };
    expect(() => registerModules([orphan])).toThrow(/module dependency missing: nonexistent/);
  });

  it('循环依赖抛错', () => {
    const a: BusinessModuleManifest = { code: 'a', name: 'A', version: '1', dependsOn: ['b'] };
    const b: BusinessModuleManifest = { code: 'b', name: 'B', version: '1', dependsOn: ['a'] };
    expect(() => registerModules([a, b])).toThrow(/dependency cycle/);
  });

  it('topo 排序后依赖在前', () => {
    const reg = registerModules([depB, depA]);
    const codes = reg.modules.map((m) => m.code);
    expect(codes.indexOf('a')).toBeLessThan(codes.indexOf('b'));
  });
});

describe('getTenantRoutes / getH5Routes', () => {
  const moduleWithRoutes: BusinessModuleManifest = {
    code: 'base',
    name: '底座',
    version: '0.0.1',
    tenantRoutes: [
      {
        path: '/workbench',
        loader: () => Promise.resolve({ default: () => null }),
      },
      {
        path: '/system/dictionaries',
        loader: () => Promise.resolve({ default: () => null }),
        permission: 'base.dict.view',
      },
    ],
    h5Routes: [
      {
        path: '/m/home',
        loader: () => Promise.resolve({ default: () => null }),
        workbenchActionUrl: '/home',
      },
    ],
  };

  it('getTenantRoutes 返回全部 tenantRoutes（不按 caps 过滤）', () => {
    const reg = registerModules([moduleWithRoutes]);
    const routes = reg.getTenantRoutes();
    expect(routes.map((r) => r.fullPath)).toEqual(['/workbench', '/system/dictionaries']);
    expect(routes[1]?.contribution.permission).toBe('base.dict.view');
  });

  it('getH5Routes 返回全部 h5Routes（含工作台卡片映射声明）', () => {
    const reg = registerModules([moduleWithRoutes]);
    const routes = reg.getH5Routes();
    expect(routes.map((r) => r.fullPath)).toEqual(['/m/home']);
    expect(routes[0]?.contribution.workbenchActionUrl).toBe('/home');
  });

  it('path 自动加 / 前缀', () => {
    const reg = registerModules([
      {
        ...moduleWithRoutes,
        tenantRoutes: [
          { path: 'no-slash', loader: () => Promise.resolve({ default: () => null }) },
        ],
      },
    ]);
    expect(reg.getTenantRoutes()[0]?.fullPath).toBe('/no-slash');
  });
});
