package com.wensaas.core.impl.ops.team;

import com.wensaas.core.api.namesource.EntityTypes;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.wensaas.core.api.audit.AuditObjects;
import com.wensaas.core.api.auth.permission.UserRoleQueryService;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.ops.access.OpsDataScope;
import com.wensaas.core.api.ops.team.OpsTeamAdminException;
import com.wensaas.core.api.ops.team.OpsTeamRoleAdminService;
import com.wensaas.core.api.spi.def.PermissionDef;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.permission.datascope.DataScope;
import com.wensaas.core.impl.permission.datascope.RoleDefaultScopeStore;
import com.wensaas.core.impl.permission.role.SysRole;
import com.wensaas.core.impl.permission.role.SysRoleMapper;
import com.wensaas.core.impl.permission.eval.SysRolePermission;
import com.wensaas.core.impl.permission.eval.SysRolePermissionMapper;
import com.wensaas.core.impl.permission.role.SysUserRole;
import com.wensaas.core.impl.permission.role.SysUserRoleMapper;
import com.wensaas.core.impl.spi.registry.PermissionRegistry;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 运营团队角色管理实现。
 *
 * <p>2 个 systemPreset 角色（OPS_SUPER_ADMIN / OPS_CSM）由迁移种入，
 * 禁止删除 / 改 code；可改 name / description / dataScope。
 *
 * <p>OPS_SUPER_ADMIN 额外收紧：权限集 API 只读（启动期 SeedRunner 按 SPI 注册全量补种，
 * 无修改必要）；成员变更仅限超管本人操作，防止普通运营账号借角色管理权限自提为超管。
 * OPS_CSM 的权限 / 成员保持可编辑。
 *
 * <p>dataScope 字符串 → DB 值：{@code "ALL"} → 1；{@code "OWNED"} → 3。
 */
public class OpsTeamRoleAdminServiceImpl implements OpsTeamRoleAdminService {

    private static final long OPS_TENANT = TenantContext.PLATFORM_TENANT_ID;
    private static final IgnoreStrategy IGNORE_TENANT_LINE =
            IgnoreStrategy.builder().tenantLine(true).build();
    private static final String SUPER_ADMIN_ROLE_CODE = "OPS_SUPER_ADMIN";
    private static final Set<String> PRESET_CODES = Set.of(
            SUPER_ADMIN_ROLE_CODE, "OPS_CSM");
    private static final String OPS_PERM_PREFIX = "ops.";

    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserMapper userMapper;
    private final RoleDefaultScopeStore defaultScopeStore;
    private final PermissionRegistry permissionRegistry;
    private final UserRoleQueryService userRoleQueryService;

    public OpsTeamRoleAdminServiceImpl(SysRoleMapper roleMapper,
                                       SysRolePermissionMapper rolePermissionMapper,
                                       SysUserRoleMapper userRoleMapper,
                                       SysUserMapper userMapper,
                                       RoleDefaultScopeStore defaultScopeStore,
                                       PermissionRegistry permissionRegistry,
                                       UserRoleQueryService userRoleQueryService) {
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.userMapper = userMapper;
        this.defaultScopeStore = defaultScopeStore;
        this.permissionRegistry = permissionRegistry;
        this.userRoleQueryService = userRoleQueryService;
    }

    @Override
    public List<OpsRoleListItem> listRoles() {
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            List<SysRole> rows = roleMapper.listForTenant(OPS_TENANT);
            return rows.stream()
                    .sorted(Comparator
                            .comparing((SysRole r) -> PRESET_CODES.contains(r.getCode()) ? 0 : 1)
                            .thenComparing(SysRole::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .map(r -> new OpsRoleListItem(
                            r.getId(), r.getCode(), r.getName(), r.getDescription(),
                            scopeToString(defaultScopeStore.get(OPS_TENANT, r.getId())),
                            r.getStatus() == null ? 1 : r.getStatus(),
                            userRoleMapper.countByRole(OPS_TENANT, r.getId()),
                            rolePermissionMapper.listCodesByRole(OPS_TENANT, r.getId()).size(),
                            PRESET_CODES.contains(r.getCode()),
                            r.getCreatedAt()))
                    .toList();
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    @Override
    public OpsRoleDetail getRole(long roleId) {
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            SysRole r = requireRole(roleId);
            List<String> perms = rolePermissionMapper.listCodesByRole(OPS_TENANT, roleId);
            int mc = userRoleMapper.countByRole(OPS_TENANT, roleId);
            return toDetail(r, mc, perms, defaultScopeStore.get(OPS_TENANT, roleId));
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    @Override
    @Transactional
    public OpsRoleDetail create(CreateRoleCommand cmd) {
        if (cmd.code() == null || cmd.code().isBlank()) {
            throw new OpsTeamAdminException(OpsTeamAdminException.Code.ROLE_CODE_CONFLICT, "code 不能为空");
        }
        if (cmd.name() == null || cmd.name().isBlank()) {
            throw new OpsTeamAdminException(OpsTeamAdminException.Code.ROLE_NAME_CONFLICT, "name 不能为空");
        }
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            if (roleMapper.countByCode(OPS_TENANT, cmd.code(), null) > 0) {
                throw new OpsTeamAdminException(OpsTeamAdminException.Code.ROLE_CODE_CONFLICT, "角色编码已存在: " + cmd.code());
            }
            if (roleMapper.countByName(OPS_TENANT, cmd.name(), null) > 0) {
                throw new OpsTeamAdminException(OpsTeamAdminException.Code.ROLE_NAME_CONFLICT, "角色名已存在: " + cmd.name());
            }
            DataScope ds = opsToDataScope(parseScope(cmd.dataScope()));
            SysRole r = new SysRole();
            r.setTenantId(OPS_TENANT);
            r.setCode(cmd.code());
            r.setName(cmd.name());
            r.setDescription(cmd.description());
            r.setStatus(1);
            r.setCreatedAt(LocalDateTime.now());
            r.setUpdatedAt(LocalDateTime.now());
            roleMapper.insert(r);
            // 运营角色默认范围同样落 sys_role_data_scope NULL-action 行（sys_role.data_scope 已退役）
            defaultScopeStore.put(OPS_TENANT, r.getId(), ds);
            return toDetail(r, 0, List.of(), ds);
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    @Override
    @Transactional
    public OpsRoleDetail update(long roleId, UpdateRoleCommand cmd) {
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            SysRole r = requireRole(roleId);
            // ② 审计对象名：加载后即登记「旧名/新名」（业务前，失败也留意图）
            AuditObjects.add(EntityTypes.SYS_ROLE, String.valueOf(roleId), r.getName(), cmd.name());
            if (cmd.name() != null && !cmd.name().equals(r.getName())) {
                if (roleMapper.countByName(OPS_TENANT, cmd.name(), roleId) > 0) {
                    throw new OpsTeamAdminException(OpsTeamAdminException.Code.ROLE_NAME_CONFLICT, "角色名已存在: " + cmd.name());
                }
                r.setName(cmd.name());
            }
            if (cmd.description() != null) r.setDescription(cmd.description());
            if (cmd.dataScope() != null) {
                defaultScopeStore.put(OPS_TENANT, roleId, opsToDataScope(parseScope(cmd.dataScope())));
            }
            r.setUpdatedAt(LocalDateTime.now());
            roleMapper.updateById(r);
            List<String> perms = rolePermissionMapper.listCodesByRole(OPS_TENANT, roleId);
            int mc = userRoleMapper.countByRole(OPS_TENANT, roleId);
            return toDetail(r, mc, perms, defaultScopeStore.get(OPS_TENANT, roleId));
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    @Override
    @Transactional
    public void delete(long roleId) {
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            SysRole r = requireRole(roleId);
            // ② 审计对象名：加载后即登记当前名（业务前，失败也留意图）
            AuditObjects.add(EntityTypes.SYS_ROLE, String.valueOf(roleId), r.getName());
            if (PRESET_CODES.contains(r.getCode())) {
                throw new OpsTeamAdminException(OpsTeamAdminException.Code.CANNOT_DELETE_PRESET_ROLE,
                        "系统预置角色不可删除: " + r.getCode());
            }
            int memberCount = userRoleMapper.countByRole(OPS_TENANT, roleId);
            if (memberCount > 0) {
                throw new OpsTeamAdminException(OpsTeamAdminException.Code.ROLE_HAS_MEMBERS,
                        "角色仍有 " + memberCount + " 名成员，请先解除关联");
            }
            rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                    .eq(SysRolePermission::getTenantId, OPS_TENANT)
                    .eq(SysRolePermission::getRoleId, roleId));
            defaultScopeStore.deleteAll(OPS_TENANT, roleId);
            roleMapper.deleteById(roleId);
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    @Override
    @Transactional
    public OpsRoleDetail updatePermissions(long roleId, List<String> permissionCodes) {
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            SysRole r = requireRole(roleId);
            // ② 审计对象名：加载后即登记当前名（业务前，失败也留意图）
            AuditObjects.add(EntityTypes.SYS_ROLE, String.valueOf(roleId), r.getName());
            if (SUPER_ADMIN_ROLE_CODE.equals(r.getCode())) {
                throw new OpsTeamAdminException(OpsTeamAdminException.Code.SUPER_ADMIN_PERMISSIONS_READONLY,
                        "超级管理员角色的权限集只读（由系统按已注册权限点维护）");
            }
            Set<String> validCodes = listOpsPermissionCodes();
            List<String> bad = new ArrayList<>();
            Set<String> dedup = new HashSet<>();
            for (String c : permissionCodes == null ? List.<String>of() : permissionCodes) {
                if (c == null || c.isBlank()) continue;
                if (!validCodes.contains(c)) bad.add(c);
                else dedup.add(c);
            }
            if (!bad.isEmpty()) {
                throw new OpsTeamAdminException(OpsTeamAdminException.Code.INVALID_PERMISSION_CODE,
                        "未知或非 ops.* 权限点: " + bad);
            }
            rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                    .eq(SysRolePermission::getTenantId, OPS_TENANT)
                    .eq(SysRolePermission::getRoleId, roleId));
            LocalDateTime now = LocalDateTime.now();
            for (String code : dedup) {
                SysRolePermission rp = new SysRolePermission();
                rp.setTenantId(OPS_TENANT);
                rp.setRoleId(roleId);
                rp.setPermissionCode(code);
                rp.setCreatedAt(now);
                rolePermissionMapper.insert(rp);
            }
            r.setUpdatedAt(now);
            roleMapper.updateById(r);
            int mc = userRoleMapper.countByRole(OPS_TENANT, roleId);
            return toDetail(r, mc, new ArrayList<>(dedup), defaultScopeStore.get(OPS_TENANT, roleId));
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    @Override
    public List<MemberRef> listMembers(long roleId) {
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            requireRole(roleId);
            List<SysUserRole> links = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getTenantId, OPS_TENANT)
                    .eq(SysUserRole::getRoleId, roleId));
            if (links.isEmpty()) return List.of();
            List<Long> userIds = links.stream().map(SysUserRole::getUserId).toList();
            List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getTenantId, OPS_TENANT)
                    .in(SysUser::getId, userIds));
            return users.stream()
                    .map(u -> new MemberRef(u.getId(), u.getUsername(), u.getRealName()))
                    .toList();
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    @Override
    @Transactional
    public void assignMembers(long roleId, List<Long> userIds) {
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            SysRole r = requireRole(roleId);
            // ② 审计对象名：加载后即登记当前名（业务前，失败也留意图）
            AuditObjects.add(EntityTypes.SYS_ROLE, String.valueOf(roleId), r.getName());
            if (SUPER_ADMIN_ROLE_CODE.equals(r.getCode()) && !operatorHoldsSuperAdminRole()) {
                throw new OpsTeamAdminException(OpsTeamAdminException.Code.CANNOT_OPERATE_SUPER_ADMIN,
                        "仅超级管理员可变更超级管理员角色的成员");
            }
            Set<Long> wanted = userIds == null ? Set.of()
                    : userIds.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
            List<SysUserRole> existing = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getTenantId, OPS_TENANT)
                    .eq(SysUserRole::getRoleId, roleId));
            Set<Long> have = existing.stream().map(SysUserRole::getUserId).collect(Collectors.toSet());

            Set<Long> toRemove = new HashSet<>(have);
            toRemove.removeAll(wanted);
            for (Long uid : toRemove) {
                userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getTenantId, OPS_TENANT)
                        .eq(SysUserRole::getRoleId, roleId)
                        .eq(SysUserRole::getUserId, uid));
            }
            Set<Long> toAdd = new HashSet<>(wanted);
            toAdd.removeAll(have);
            for (Long uid : toAdd) {
                SysUserRole link = new SysUserRole();
                link.setTenantId(OPS_TENANT);
                link.setUserId(uid);
                link.setRoleId(roleId);
                link.setCreatedAt(LocalDateTime.now());
                userRoleMapper.insert(link);
            }
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    // ===== helpers =====

    /** 当前操作者是否持有 OPS_SUPER_ADMIN 角色（未认证上下文视为否）。 */
    private boolean operatorHoldsSuperAdminRole() {
        UserPrincipal me = UserContext.get();
        if (me == null) return false;
        return userRoleQueryService.rolesOfUser(OPS_TENANT, me.userId())
                .stream().anyMatch(role -> SUPER_ADMIN_ROLE_CODE.equals(role.code()));
    }

    private SysRole requireRole(long roleId) {
        SysRole r = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, OPS_TENANT)
                .eq(SysRole::getId, roleId));
        if (r == null) {
            throw new OpsTeamAdminException(OpsTeamAdminException.Code.ROLE_NOT_FOUND, "角色不存在");
        }
        return r;
    }

    private Set<String> listOpsPermissionCodes() {
        return permissionRegistry.getAll().stream()
                .map(PermissionDef::code)
                .filter(c -> c != null && c.startsWith(OPS_PERM_PREFIX))
                .collect(Collectors.toSet());
    }

    private OpsRoleDetail toDetail(SysRole r, int memberCount, List<String> permissions, DataScope defaultScope) {
        return new OpsRoleDetail(
                r.getId(), r.getCode(), r.getName(), r.getDescription(),
                scopeToString(defaultScope),
                r.getStatus() == null ? 1 : r.getStatus(),
                memberCount, PRESET_CODES.contains(r.getCode()),
                permissions,
                r.getCreatedAt(), r.getUpdatedAt());
    }

    private static OpsDataScope parseScope(String s) {
        if (s == null) return OpsDataScope.OPS_ALL;
        return switch (s.toUpperCase()) {
            case "ALL", "OPS_ALL" -> OpsDataScope.OPS_ALL;
            case "OWNED", "OPS_OWNED", "SELF" -> OpsDataScope.OPS_OWNED;
            default -> throw new OpsTeamAdminException(OpsTeamAdminException.Code.INVALID_DATA_SCOPE,
                    "运营 dataScope 仅支持 ALL / OWNED，收到：" + s);
        };
    }

    /** 运营域 → 租户域：dbValue 同源（OPS_ALL=1=ALL；OPS_OWNED=3=SELF），统一存进 sys_role_data_scope。 */
    private static DataScope opsToDataScope(OpsDataScope ops) {
        return DataScope.fromCode(ops.dbValue());
    }

    /** 租户域 → 运营域展示串：ALL→"ALL"，其余（运营域只会有 SELF）→"OWNED"。 */
    private static String scopeToString(DataScope ds) {
        return ds == DataScope.ALL ? "ALL" : "OWNED";
    }
}
