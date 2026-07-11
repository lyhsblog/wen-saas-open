package com.wensaas.core.impl.permission.eval;

import com.wensaas.core.impl.permission.datascope.DataScope;

import java.util.Objects;
import java.util.Set;

/**
 * 单个 action（功能点）的有效数据范围快照：范围 + 已按范围物化好的部门集合。
 * 由 {@link PermissionResolver#scopeFor} 按「授予该功能点的角色」聚合（grant-coupled），供数据权限拦截器直接拼 WHERE：
 * <ul>
 *   <li>ALL / SELF：{@code deptIds} 为空</li>
 *   <li>DEPT_AND_BELOW：{@code deptIds} = 直属部门 + 全部子孙（已展开）</li>
 *   <li>DEPT_ONLY：{@code deptIds} = 直属部门（未展开）</li>
 * </ul>
 */
public record EffectiveScope(DataScope scope, Set<Long> deptIds) {

    public EffectiveScope {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(deptIds, "deptIds");
        deptIds = Set.copyOf(deptIds);
    }
}
