package com.wensaas.core.impl.permission.role;

import com.wensaas.core.api.namesource.EntityTypes;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wensaas.core.api.audit.AuditObjects;
import com.wensaas.core.api.auth.permission.RoleAdminException;
import com.wensaas.core.api.auth.permission.RoleAdminService;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.wensaas.core.impl.permission.datascope.DataScope;
import com.wensaas.core.impl.permission.datascope.RoleDefaultScopeStore;
import com.wensaas.core.impl.permission.datascope.SysRoleDataScope;
import com.wensaas.core.impl.permission.datascope.SysRoleDataScopeMapper;
import com.wensaas.core.impl.permission.eval.SysRolePermission;
import com.wensaas.core.impl.permission.eval.SysRolePermissionMapper;

/**
 * 角色管理（写）实现。
 *
 * <p>系统预置角色 code：
 * <ul>
 *   <li>{@code SYSTEM_OWNER} 主管理员 —— 完全只读（包括 name/dataScope/permissions/不可删）</li>
 *   <li>{@code SYSTEM_ADMIN} 管理员 / {@code SYSTEM_MEMBER} 普通员工 —— 可改但不可删</li>
 * </ul>
 *
 * <p>权限点全量替换走"delete + 逐条 insert"（同事务）；权限解析为请求级直查，角色 / 成员变更无需缓存失效。
 */
public class RoleAdminServiceImpl implements RoleAdminService {

    private static final String CODE_OWNER = "SYSTEM_OWNER";
    private static final Set<String> SYSTEM_PRESET_CODES =
            new HashSet<>(Arrays.asList(CODE_OWNER, "SYSTEM_ADMIN", "SYSTEM_MEMBER"));

    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserMapper userMapper;
    private final SysRoleDataScopeMapper roleDataScopeMapper;
    private final RoleDefaultScopeStore defaultScopeStore;
    private final com.wensaas.core.impl.pkg.PackageModuleGate packageGate;

    public RoleAdminServiceImpl(SysRoleMapper roleMapper,
                                 SysRolePermissionMapper rolePermissionMapper,
                                 SysUserRoleMapper userRoleMapper,
                                 SysUserMapper userMapper,
                                 SysRoleDataScopeMapper roleDataScopeMapper,
                                 RoleDefaultScopeStore defaultScopeStore,
                                 com.wensaas.core.impl.pkg.PackageModuleGate packageGate) {
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.userMapper = userMapper;
        this.roleDataScopeMapper = roleDataScopeMapper;
        this.defaultScopeStore = defaultScopeStore;
        this.packageGate = packageGate;
    }

    @Override
    public List<RoleView> listRoles() {
        long tenantId = TenantContext.require();
        List<SysRole> rows = roleMapper.listForTenant(tenantId);
        return rows.stream().map(r -> {
            int mc = userRoleMapper.countByRole(tenantId, r.getId());
            return toView(r, mc, defaultScopeStore.get(tenantId, r.getId()));
        }).toList();
    }

    @Override
    public RoleDetail getRole(long roleId) {
        long tenantId = TenantContext.require();
        SysRole row = requireRole(tenantId, roleId);
        List<String> perms = rolePermissionMapper.listCodesByRole(tenantId, roleId);
        int mc = userRoleMapper.countByRole(tenantId, roleId);
        return toDetail(row, mc, perms, defaultScopeStore.get(tenantId, roleId));
    }

    @Override
    @Transactional
    public RoleDetail create(CreateRoleRequest req) {
        requireNonBlank("code", req.code());
        requireNonBlank("name", req.name());
        long tenantId = TenantContext.require();

        if (roleMapper.countByCode(tenantId, req.code(), null) > 0) {
            throw new RoleAdminException(RoleAdminException.Code.ROLE_CODE_DUPLICATE,
                    "角色编码已存在: " + req.code());
        }
        if (roleMapper.countByName(tenantId, req.name(), null) > 0) {
            throw new RoleAdminException(RoleAdminException.Code.ROLE_NAME_DUPLICATE,
                    "角色名已存在: " + req.name());
        }
        DataScope ds = parseDataScope(req.dataScope());

        SysRole row = new SysRole();
        row.setTenantId(tenantId);
        row.setCode(req.code());
        row.setName(req.name());
        row.setDescription(req.description());
        row.setStatus(1);
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        row.setCreatedBy(currentUserId());
        row.setUpdatedBy(currentUserId());
        roleMapper.insert(row);
        // 角色默认数据范围唯一权威落点：sys_role_data_scope NULL-action 行（sys_role.data_scope 已退役）
        defaultScopeStore.put(tenantId, row.getId(), ds);
        return toDetail(row, 0, List.of(), ds);
    }

    @Override
    @Transactional
    public RoleDetail update(long roleId, UpdateRoleRequest req) {
        long tenantId = TenantContext.require();
        SysRole row = requireRole(tenantId, roleId);
        // ② 审计对象名：加载后即登记「旧名/新名」（业务前，失败也留意图）
        AuditObjects.add(EntityTypes.SYS_ROLE, String.valueOf(roleId), row.getName(), req.name());
        boolean isOwner = CODE_OWNER.equals(row.getCode());
        if (isOwner) {
            // 主管理员完全只读
            throw new RoleAdminException(RoleAdminException.Code.ROLE_SYSTEM_OWNER_READONLY,
                    "主管理员角色完全只读");
        }
        if (req.name() != null) {
            requireNonBlank("name", req.name());
            if (roleMapper.countByName(tenantId, req.name(), roleId) > 0) {
                throw new RoleAdminException(RoleAdminException.Code.ROLE_NAME_DUPLICATE,
                        "角色名已存在: " + req.name());
            }
            row.setName(req.name());
        }
        if (req.description() != null) {
            row.setDescription(req.description());
        }
        if (req.dataScope() != null) {
            DataScope newDs = parseDataScope(req.dataScope());
            // 角色默认数据范围唯一权威落点：sys_role_data_scope NULL-action 行
            defaultScopeStore.put(tenantId, roleId, newDs);
        }
        row.setUpdatedAt(LocalDateTime.now());
        row.setUpdatedBy(currentUserId());
        roleMapper.updateById(row);
        return toDetail(row, userRoleMapper.countByRole(tenantId, roleId),
                rolePermissionMapper.listCodesByRole(tenantId, roleId),
                defaultScopeStore.get(tenantId, roleId));
    }

    @Override
    @Transactional
    public void delete(long roleId) {
        long tenantId = TenantContext.require();
        SysRole row = requireRole(tenantId, roleId);
        // ② 审计对象名：加载后即登记当前名（业务前，失败也留意图）
        AuditObjects.add(EntityTypes.SYS_ROLE, String.valueOf(roleId), row.getName());
        if (SYSTEM_PRESET_CODES.contains(row.getCode())) {
            throw new RoleAdminException(
                    CODE_OWNER.equals(row.getCode())
                            ? RoleAdminException.Code.ROLE_SYSTEM_OWNER_READONLY
                            : RoleAdminException.Code.ROLE_SYSTEM_READONLY_FIELDS,
                    "系统预置角色不可删除");
        }
        int members = userRoleMapper.countByRole(tenantId, roleId);
        if (members > 0) {
            throw new RoleAdminException(RoleAdminException.Code.ROLE_HAS_MEMBERS,
                    "角色下仍有 " + members + " 个成员");
        }
        rolePermissionMapper.deleteByRole(tenantId, roleId);
        defaultScopeStore.deleteAll(tenantId, roleId);
        roleMapper.deleteById(roleId); // @TableLogic 软删
    }

    @Override
    @Transactional
    public RoleDetail setStatus(long roleId, boolean enabled) {
        long tenantId = TenantContext.require();
        SysRole row = requireRole(tenantId, roleId);
        if (CODE_OWNER.equals(row.getCode()) && !enabled) {
            throw new RoleAdminException(RoleAdminException.Code.ROLE_SYSTEM_OWNER_READONLY,
                    "主管理员角色不可停用");
        }
        row.setStatus(enabled ? 1 : 0);
        row.setUpdatedAt(LocalDateTime.now());
        row.setUpdatedBy(currentUserId());
        roleMapper.updateById(row);
        return toDetail(row, userRoleMapper.countByRole(tenantId, roleId),
                rolePermissionMapper.listCodesByRole(tenantId, roleId),
                defaultScopeStore.get(tenantId, roleId));
    }

    @Override
    @Transactional
    public RoleDetail updatePermissions(long roleId, List<String> permissionCodes) {
        long tenantId = TenantContext.require();
        SysRole row = requireRole(tenantId, roleId);
        if (CODE_OWNER.equals(row.getCode())) {
            throw new RoleAdminException(RoleAdminException.Code.ROLE_SYSTEM_OWNER_READONLY,
                    "主管理员角色权限完全只读");
        }
        // 套餐门控保留：提交的码只覆盖「当前可见」范围，对角色原有但属于未开通受控模块的码原样保留，
        // 避免退订套餐后一次保存把历史授权静默吊销（运行期仍 402 拦住，续期即恢复）。
        Set<String> codes = permissionCodes == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(permissionCodes);
        Set<String> enabledModules = packageGate.enabledModuleCodes(tenantId);
        Set<String> gated = packageGate.gatedModuleCodes();
        for (String existing : rolePermissionMapper.listCodesByRole(tenantId, roleId)) {
            if (packageGate.hidden(existing, enabledModules, gated)) {
                codes.add(existing);
            }
        }

        rolePermissionMapper.deleteByRole(tenantId, roleId);
        Long creator = currentUserId();
        for (String code : codes) {
            if (code == null || code.isBlank()) continue;
            SysRolePermission rp = new SysRolePermission();
            rp.setTenantId(tenantId);
            rp.setRoleId(roleId);
            rp.setPermissionCode(code.trim());
            rp.setCreatedAt(LocalDateTime.now());
            rp.setCreatedBy(creator);
            rolePermissionMapper.insert(rp);
        }        return toDetail(row, userRoleMapper.countByRole(tenantId, roleId),
                rolePermissionMapper.listCodesByRole(tenantId, roleId),
                defaultScopeStore.get(tenantId, roleId));
    }

    @Override
    public List<MemberRef> listMembers(long roleId) {
        long tenantId = TenantContext.require();
        requireRole(tenantId, roleId);
        List<Long> userIds = userRoleMapper.listUserIdsByRole(tenantId, roleId);
        if (userIds.isEmpty()) {
            return List.of();
        }
        // 关联 sys_user 补展示名（姓名为主、账号为辅），租户线自动隔离当前租户成员。
        Map<Long, SysUser> userById = userMapper.selectList(
                        Wrappers.<SysUser>lambdaQuery().in(SysUser::getId, userIds)).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));
        return userIds.stream()
                .map(id -> {
                    SysUser u = userById.get(id);
                    return new MemberRef(id, u != null ? u.getUsername() : null,
                            u != null ? u.getRealName() : null);
                })
                .toList();
    }

    @Override
    @Transactional
    public RoleDetail assignMembers(long roleId, List<Long> userIds) {
        long tenantId = TenantContext.require();
        SysRole row = requireRole(tenantId, roleId);
        if (userIds == null || userIds.isEmpty()) {
            return toDetail(row, userRoleMapper.countByRole(tenantId, roleId),
                    rolePermissionMapper.listCodesByRole(tenantId, roleId),
                    defaultScopeStore.get(tenantId, roleId));
        }
        Set<Long> existing = new HashSet<>(userRoleMapper.listUserIdsByRole(tenantId, roleId));
        Long creator = currentUserId();
        for (Long uid : new LinkedHashSet<>(userIds)) {
            if (uid == null || existing.contains(uid)) continue;
            SysUserRole ur = new SysUserRole();
            ur.setTenantId(tenantId);
            ur.setUserId(uid);
            ur.setRoleId(roleId);
            ur.setCreatedAt(LocalDateTime.now());
            ur.setCreatedBy(creator);
            userRoleMapper.insert(ur);
        }
        return toDetail(row, userRoleMapper.countByRole(tenantId, roleId),
                rolePermissionMapper.listCodesByRole(tenantId, roleId),
                defaultScopeStore.get(tenantId, roleId));
    }

    @Override
    @Transactional
    public RoleDetail removeMembers(long roleId, List<Long> userIds) {
        long tenantId = TenantContext.require();
        SysRole row = requireRole(tenantId, roleId);
        if (userIds != null && !userIds.isEmpty()) {
            userRoleMapper.deleteByRoleAndUsers(tenantId, roleId, userIds);
        }
        return toDetail(row, userRoleMapper.countByRole(tenantId, roleId),
                rolePermissionMapper.listCodesByRole(tenantId, roleId),
                defaultScopeStore.get(tenantId, roleId));
    }

    @Override
    public RoleDataScopeView getRoleDataScope(long roleId) {
        long tenantId = TenantContext.require();
        requireRole(tenantId, roleId);
        List<SysRoleDataScope> rows = roleDataScopeMapper.selectList(
                new LambdaQueryWrapper<SysRoleDataScope>()
                        .eq(SysRoleDataScope::getTenantId, tenantId)
                        .eq(SysRoleDataScope::getRoleId, roleId));
        String defaultScope = DataScope.SELF.name();
        Map<String, String> actionOverrides = new LinkedHashMap<>();
        for (SysRoleDataScope row : rows) {
            String scopeName = DataScope.fromCode(row.getDataScope()).name();
            if (row.getAction() == null) {
                defaultScope = scopeName;
            } else {
                actionOverrides.put(row.getAction(), scopeName);
            }
        }
        return new RoleDataScopeView(defaultScope, actionOverrides);
    }

    @Override
    @Transactional
    public void saveRoleDataScope(long roleId, SaveDataScopeCmd cmd) {
        long tenantId = TenantContext.require();
        SysRole roleRow = requireRole(tenantId, roleId);
        if (CODE_OWNER.equals(roleRow.getCode())) {
            throw new RoleAdminException(RoleAdminException.Code.ROLE_SYSTEM_OWNER_READONLY,
                    "主管理员角色数据范围完全只读");
        }
        DataScope defaultDs = parseDataScope(cmd.defaultScope());

        // 全量替换：删除所有现有行，再插入新配置
        roleDataScopeMapper.delete(new LambdaQueryWrapper<SysRoleDataScope>()
                .eq(SysRoleDataScope::getTenantId, tenantId)
                .eq(SysRoleDataScope::getRoleId, roleId));

        // NULL-action 行 = 角色默认
        SysRoleDataScope nullRow = new SysRoleDataScope();
        nullRow.setTenantId(tenantId);
        nullRow.setRoleId(roleId);
        nullRow.setAction(null);
        nullRow.setDataScope(defaultDs.code());
        roleDataScopeMapper.insert(nullRow);

        // action 覆盖行
        if (cmd.actionOverrides() != null) {
            for (Map.Entry<String, String> e : cmd.actionOverrides().entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank()) continue;
                SysRoleDataScope overrideRow = new SysRoleDataScope();
                overrideRow.setTenantId(tenantId);
                overrideRow.setRoleId(roleId);
                overrideRow.setAction(e.getKey().trim());
                overrideRow.setDataScope(parseDataScope(e.getValue()).code());
                roleDataScopeMapper.insert(overrideRow);
            }
        }

        // 角色默认范围已落 NULL-action 行（sys_role.data_scope 已退役）；仅刷角色审计字段
        roleRow.setUpdatedAt(LocalDateTime.now());
        roleRow.setUpdatedBy(currentUserId());
        roleMapper.updateById(roleRow);
    }

    // ===================== helpers =====================

    private SysRole requireRole(long tenantId, long roleId) {
        SysRole row = roleMapper.selectById(roleId);
        if (row == null || row.getTenantId() != tenantId) {
            throw new RoleAdminException(RoleAdminException.Code.ROLE_NOT_FOUND,
                    "角色不存在: " + roleId);
        }
        return row;
    }

    private static DataScope parseDataScope(String s) {
        if (s == null) {
            throw new RoleAdminException(RoleAdminException.Code.INVALID_DATA_SCOPE, "dataScope 不能为空");
        }
        try {
            return DataScope.valueOf(s.trim());
        } catch (IllegalArgumentException e) {
            throw new RoleAdminException(RoleAdminException.Code.INVALID_DATA_SCOPE,
                    "无效 dataScope: " + s);
        }
    }

    private static Long currentUserId() {
        com.wensaas.core.api.auth.user.UserPrincipal p = UserContext.get();
        return p == null ? null : p.userId();
    }

    private static RoleView toView(SysRole r, int memberCount, DataScope defaultScope) {
        return new RoleView(
                r.getId(),
                r.getCode(),
                r.getName(),
                r.getDescription(),
                defaultScope.name(),
                r.getStatus() == null ? 1 : r.getStatus(),
                memberCount,
                SYSTEM_PRESET_CODES.contains(r.getCode()),
                r.getCreatedAt());
    }

    private static RoleDetail toDetail(SysRole r, int memberCount, List<String> perms, DataScope defaultScope) {
        return new RoleDetail(
                r.getId(),
                r.getCode(),
                r.getName(),
                r.getDescription(),
                defaultScope.name(),
                r.getStatus() == null ? 1 : r.getStatus(),
                memberCount,
                SYSTEM_PRESET_CODES.contains(r.getCode()),
                perms,
                r.getCreatedAt(),
                r.getUpdatedAt());
    }

    private static void requireNonBlank(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }
}
