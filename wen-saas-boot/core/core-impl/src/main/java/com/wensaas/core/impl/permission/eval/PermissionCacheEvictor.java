package com.wensaas.core.impl.permission.eval;

import com.wensaas.core.impl.permission.org.DeptService;

/**
 * 权限缓存失效器。
 *
 * <p>权限点 / 角色 / 数据范围已改为请求级直查 DB（{@link RequestScopedPermissionResolver}），无跨请求缓存可清——
 * 故角色改权限 / 用户改角色等写操作不再需要失效动作。唯一跨请求缓存是整租户部门树（{@link DeptService}），
 * 仅在部门结构变更（增删 / 移动 / 启停）时按租户失效。
 */
public class PermissionCacheEvictor {

    private final DeptService deptService;

    public PermissionCacheEvictor(DeptService deptService) {
        this.deptService = deptService;
    }

    /** 部门树结构变更（增删 / 移动 / 启停）：每租户一个 key。 */
    public void evictDeptTree(long tenantId) {
        deptService.evict(tenantId);
    }

    /** 清空部门树缓存（大范围变更 / 测试复位等少数场景；非热路径）。 */
    public void evictAll() {
        deptService.evictAll();
    }
}
