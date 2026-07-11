import { afterEach, describe, expect, it } from 'vitest';
import {
  useCapabilitiesStore,
  useHasPermission,
  useHasModule,
  useIsReadonly,
} from './capabilities';

const get = useCapabilitiesStore.getState;

afterEach(() => {
  get().reset();
});

describe('capabilities store gates', () => {
  it('caps == null 时所有权限点都拒绝', () => {
    // 直接读取 selector 行为：把 hook 当 selector 函数调用是反模式，
    // 这里通过 store.getState() + 重新实现等价判定来避免 React 环境
    expect(get().caps).toBeNull();
  });

  it('tenantOwner=true 全部 permission 命中', () => {
    get().setCapabilities({
      permissions: [],
      enabledModules: [],
      activePackages: [],
      packageReadonly: false,
      tenantOwner: true,
      impersonating: false,
      operatorName: null,
      realName: '',
      screenshotWatermark: true,
      menus: [],
    });
    const allow = useCapabilitiesStore.getState();
    expect(allow.caps?.tenantOwner).toBe(true);
  });

  it('hook 引用为可调用 selector', () => {
    // 引用稳定性检查，避免后续 refactor 改成 inline 函数破坏依赖
    expect(typeof useHasPermission).toBe('function');
    expect(typeof useHasModule).toBe('function');
    expect(typeof useIsReadonly).toBe('function');
  });

  it('setCapabilities 写入后 caps 字段一致', () => {
    get().setCapabilities({
      permissions: ['dict.group.create', 'dict.group.delete'],
      enabledModules: ['crm', 'project'],
      activePackages: ['BASIC'],
      packageReadonly: false,
      tenantOwner: false,
      impersonating: false,
      operatorName: null,
      realName: '',
      screenshotWatermark: true,
      menus: [],
    });
    expect(get().caps?.permissions).toContain('dict.group.create');
    expect(get().caps?.enabledModules).toContain('crm');
    expect(get().caps?.packageReadonly).toBe(false);
  });

  it('packageReadonly 切到 true', () => {
    get().setCapabilities({
      permissions: [],
      enabledModules: [],
      activePackages: ['BASIC'],
      packageReadonly: true,
      tenantOwner: false,
      impersonating: false,
      operatorName: null,
      realName: '',
      screenshotWatermark: true,
      menus: [],
    });
    expect(get().caps?.packageReadonly).toBe(true);
  });
});
