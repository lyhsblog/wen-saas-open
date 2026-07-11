package com.wensaas.core.impl.permission.eval;

import java.util.Objects;
import java.util.Set;
import com.wensaas.core.impl.permission.datascope.DataScope;

/**
 * 用户全量权限快照（{@link PermissionResolver#resolve} 返回）。供登录态 capabilities 与运营访问上下文使用——
 * 只在请求内复用，跨请求不缓存。
 *
 * <p>单权限点判定与单 action 数据范围不走本快照（避免无谓全量装配），分别走
 * {@link PermissionResolver#has}/{@link PermissionResolver#hasAny} 与 {@link PermissionResolver#scopeFor}。
 *
 * @param permissions 用户拥有的全部权限点编码（多角色取并集）
 * @param scope       用户的默认数据范围（各启用角色 action=NULL 默认行取最宽松）
 * @param deptIds     默认范围为 DEPT_AND_BELOW 时的可见部门集合（已展开含子孙）；否则空集
 */
public record UserPermissions(Set<String> permissions, DataScope scope, Set<Long> deptIds) {

    public UserPermissions {
        Objects.requireNonNull(permissions, "permissions");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(deptIds, "deptIds");
        permissions = Set.copyOf(permissions);
        deptIds = Set.copyOf(deptIds);
    }

    public boolean hasPermission(String code) {
        return permissions.contains(code);
    }

    public boolean hasAny(String[] codes) {
        for (String c : codes) {
            if (permissions.contains(c)) {
                return true;
            }
        }
        return false;
    }
}
