package com.wensaas.core.impl.permission.datascope;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.util.List;
import com.wensaas.core.impl.permission.role.RoleAdminServiceImpl;
import com.wensaas.core.impl.permission.role.TenantRecommendedRoleServiceImpl;

/**
 * 角色默认数据范围存取门面：统一读写 {@code sys_role_data_scope} 的 {@code action = NULL} 行。
 *
 * <p>背景：{@code sys_role.data_scope} 列退役后，角色默认数据范围唯一权威落点是
 * {@code sys_role_data_scope} 的 NULL-action 行。租户角色（{@code RoleAdminServiceImpl}）、运营角色
 * （{@code OpsTeamRoleAdminServiceImpl} / {@code TenantOpsServiceImpl}）、推荐角色激活
 * （{@code TenantRecommendedRoleServiceImpl}）等多个写入点都需要维护这一行；本类把"读默认 / 落默认"
 * 收敛到一处，避免每个服务各写一份 upsert 样板。
 *
 * <p>NULL-action 行的"每角色至多一条"由 DB 局部唯一索引 {@code uk_sys_role_data_scope_role_default}
 * 保证（见 V1.0.6 迁移）。
 */
public class RoleDefaultScopeStore {

    private final SysRoleDataScopeMapper mapper;

    public RoleDefaultScopeStore(SysRoleDataScopeMapper mapper) {
        this.mapper = mapper;
    }

    /** 读角色默认数据范围；无 NULL-action 行时返回 fail-safe 兜底 {@link DataScope#SELF}。 */
    public DataScope get(long tenantId, long roleId) {
        SysRoleDataScope row = findNullRow(tenantId, roleId);
        return row == null ? DataScope.SELF : DataScope.fromCode(row.getDataScope());
    }

    /** upsert 角色默认数据范围（NULL-action 行）。存在则改、不存在则插。 */
    public void put(long tenantId, long roleId, DataScope scope) {
        SysRoleDataScope row = findNullRow(tenantId, roleId);
        if (row == null) {
            row = new SysRoleDataScope();
            row.setTenantId(tenantId);
            row.setRoleId(roleId);
            row.setAction(null);
            row.setDataScope(scope.code());
            mapper.insert(row);
        } else {
            row.setDataScope(scope.code());
            mapper.updateById(row);
        }
    }

    /** 删除角色全部数据范围行（默认 + 各功能点覆盖）。角色删除时级联清理用。 */
    public void deleteAll(long tenantId, long roleId) {
        mapper.delete(new LambdaQueryWrapper<SysRoleDataScope>()
                .eq(SysRoleDataScope::getTenantId, tenantId)
                .eq(SysRoleDataScope::getRoleId, roleId));
    }

    private SysRoleDataScope findNullRow(long tenantId, long roleId) {
        List<SysRoleDataScope> rows = mapper.selectList(new LambdaQueryWrapper<SysRoleDataScope>()
                .eq(SysRoleDataScope::getTenantId, tenantId)
                .eq(SysRoleDataScope::getRoleId, roleId)
                .isNull(SysRoleDataScope::getAction));
        return rows.isEmpty() ? null : rows.get(0);
    }
}
