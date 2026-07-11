package com.wensaas.core.impl.ops.team;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.wensaas.core.api.spi.def.PermissionDef;
import com.wensaas.core.api.tenant.TenantContext;import com.wensaas.core.impl.permission.role.SysRole;
import com.wensaas.core.impl.permission.role.SysRoleMapper;
import com.wensaas.core.impl.permission.eval.SysRolePermission;
import com.wensaas.core.impl.permission.eval.SysRolePermissionMapper;
import com.wensaas.core.impl.spi.registry.PermissionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 运营 2 预置角色的权限点种子（启动期，idempotent）。
 *
 * <p>初始化数据已种入角色行；本 Runner 启动时按 SPI 已注册的 {@code ops.*} 权限点
 * 按白名单写入 {@code sys_role_permission}：
 * <ul>
 *   <li>{@code OPS_SUPER_ADMIN} → 全部 {@code ops.*}</li>
 *   <li>{@code OPS_CSM} → dashboard.view + tenant.list/update/impersonate/suspend/reset_password
 *       + operation_log.list + health/monitor.view + ticket.view/handle（工单由客户经理承接，按 OWNED 范围收敛到名下租户）</li>
 * </ul>
 *
 * <p>仅插入缺失的绑定（按 (role_id, permission_code) 去重）；不删除已有的，
 * 因此对运营手动调权后再重启是安全的。
 */
public class OpsTeamRoleSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OpsTeamRoleSeedRunner.class);
    private static final long OPS_TENANT = TenantContext.PLATFORM_TENANT_ID;
    private static final IgnoreStrategy IGNORE_TENANT_LINE =
            IgnoreStrategy.builder().tenantLine(true).build();
    private static final String OPS_PREFIX = "ops.";

    /** CSM 默认白名单。 */
    private static final Set<String> CSM_DEFAULT = Set.of(
            "ops.dashboard.view",
            "ops.tenant.list",
            "ops.tenant.update",
            "ops.tenant.impersonate",
            "ops.tenant.suspend",
            "ops.tenant.reset_password",
            "ops.operation_log.list",
            "ops.health.view",
            "ops.monitor.view",
            "ops.ticket.view",
            "ops.ticket.handle"
    );

    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final PermissionRegistry permissionRegistry;

    public OpsTeamRoleSeedRunner(SysRoleMapper roleMapper,
                                  SysRolePermissionMapper rolePermissionMapper,
                                  PermissionRegistry permissionRegistry) {
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.permissionRegistry = permissionRegistry;
    }

    @Override
    @Transactional
    public void run(org.springframework.boot.ApplicationArguments args) {
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            Set<String> allOps = new HashSet<>();
            for (PermissionDef d : permissionRegistry.getAll()) {
                if (d.code() != null && d.code().startsWith(OPS_PREFIX)) {
                    allOps.add(d.code());
                }
            }
            if (allOps.isEmpty()) {
                log.info("ops.role-seed.skip reason=no-ops-permissions-registered");
                return;
            }

            int totalInserted = 0;
            totalInserted += grant("OPS_SUPER_ADMIN", allOps);
            totalInserted += grant("OPS_CSM", intersect(allOps, CSM_DEFAULT));
            log.info("ops.role-seed.done totalInserted={} totalOpsPermissions={}", totalInserted, allOps.size());
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    private int grant(String roleCode, Set<String> wantedCodes) {
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, OPS_TENANT)
                .eq(SysRole::getCode, roleCode));
        if (role == null) {
            log.warn("ops.role-seed.skip role={} reason=role-not-found（初始化数据应已种入）", roleCode);
            return 0;
        }
        List<String> existing = rolePermissionMapper.listCodesByRole(OPS_TENANT, role.getId());
        Set<String> have = new HashSet<>(existing);
        Set<String> toAdd = new HashSet<>(wantedCodes);
        toAdd.removeAll(have);
        if (toAdd.isEmpty()) return 0;
        LocalDateTime now = LocalDateTime.now();
        for (String code : toAdd) {
            SysRolePermission rp = new SysRolePermission();
            rp.setTenantId(OPS_TENANT);
            rp.setRoleId(role.getId());
            rp.setPermissionCode(code);
            rp.setCreatedAt(now);
            rolePermissionMapper.insert(rp);
        }
        log.info("ops.role-seed.granted role={} added={}", roleCode, toAdd.size());
        return toAdd.size();
    }

    private static Set<String> intersect(Set<String> a, Set<String> b) {
        Set<String> out = new HashSet<>(a);
        out.retainAll(b);
        return out;
    }
}
