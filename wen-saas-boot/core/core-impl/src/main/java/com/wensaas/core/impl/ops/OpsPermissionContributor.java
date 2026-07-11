package com.wensaas.core.impl.ops;

import com.wensaas.core.api.spi.def.PermissionDef;
import com.wensaas.core.impl.spi.registry.PermissionRegistry;

import java.util.List;

/**
 * 运营域权限点贡献器。
 *
 * <p>运营专属权限点 {@code ops.*} 由本 bean 注册到 {@link PermissionRegistry}，
 * 不再借业务模块 SPI（拆 ops 出业务模块体系）：故运营既不进 {@code sys_business_module}、
 * 也不出现在运营「业务模块管理」页。运营子树 scope=OPS、不参与套餐。
 *
 * <p><b>注册时机</b>：在<b>构造期</b>写入 {@link PermissionRegistry}（依赖注入保证 registry 先建），
 * 早于一切 {@code SmartInitializingSingleton}（如 {@code MenuPermissionConsistencyValidator}
 * 读权威码集合）与 {@code ApplicationRunner}（如 {@code OpsTeamRoleSeedRunner} 给运营角色种权限），
 * 故这两个读取方都能看到完整的 ops.* 集合。
 *
 * <p>{@code RequirePermissionAspect} 对运营账号（{@code tenantOwner=false}）走 {@code PermissionResolver}，
 * 查 {@code sys_role_permission}（{@code tenant_id=0}）匹配。命名约定 {@code ops.<资源>.<动作>}。
 */
public class OpsPermissionContributor {

    /** ops.* 在 {@link PermissionRegistry} 的所有者键（非业务模块，仅用于 owner 归属标识）。 */
    public static final String OWNER = "ops";

    public OpsPermissionContributor(PermissionRegistry permissionRegistry) {
        permissions().forEach(p -> permissionRegistry.register(OWNER, p));
    }

    /** 运营专属权限点清单（ops.*）。 */
    public static List<PermissionDef> permissions() {
        return List.of(
                // 工作台
                new PermissionDef("ops.dashboard.view", "工作台 · 查看", "运营首页指标 / 待办 / 动态"),

                // 团队成员
                new PermissionDef("ops.team.member.list",          "团队成员 · 列表",          null),
                new PermissionDef("ops.team.member.create",        "团队成员 · 新建",          null),
                new PermissionDef("ops.team.member.update",        "团队成员 · 编辑",          null),
                new PermissionDef("ops.team.member.disable",       "团队成员 · 停用/启用",     null),
                new PermissionDef("ops.team.member.reset_password","团队成员 · 重置密码",      null),
                new PermissionDef("ops.team.member.unbind_oauth",  "团队成员 · 解绑 OAuth",    null),
                new PermissionDef("ops.team.member.contact.reveal","团队成员 · 查看完整联系方式", "解密查看团队成员完整手机 / 邮箱（敏感操作，记审计）"),

                // 角色与权限
                new PermissionDef("ops.team.role.list",              "运营角色 · 列表",         null),
                new PermissionDef("ops.team.role.create",            "运营角色 · 新建",         null),
                new PermissionDef("ops.team.role.update",            "运营角色 · 编辑",         null),
                new PermissionDef("ops.team.role.delete",            "运营角色 · 删除",         null),
                new PermissionDef("ops.team.role.assign_permission", "运营角色 · 权限赋值",     null),
                new PermissionDef("ops.team.role.assign_member",     "运营角色 · 成员分配",     null),

                // 系统参数
                new PermissionDef("ops.setting.view",   "系统参数 · 查看", null),
                new PermissionDef("ops.setting.update", "系统参数 · 修改", null),

                // 操作日志
                new PermissionDef("ops.operation_log.list", "操作日志 · 查询", null),

                // 菜单管理（运营自助维护 sys_menu 树）
                new PermissionDef("ops.menu.manage", "菜单管理 · 维护",
                        "增改 / 移动 / 排序 / 启停 / 绑权限码（兑现「加菜单不必后端发版」）"),

                // 客户健康度（纯信息看板）
                new PermissionDef("ops.health.view", "客户健康度 · 查看", "健康度总览 / 列表 / 详情"),

                // 平台运维监控（跨模块业务指标看板，只读）
                new PermissionDef("ops.monitor.view", "平台监控 · 查看",
                        "平台运维监控看板：KPI / 趋势 / 模块使用 / 套餐分布 / 续费率 / 资源 / API·Webhook"),

                // 租户管理
                new PermissionDef("ops.tenant.list",           "租户管理 · 列表",       null),
                new PermissionDef("ops.tenant.create",         "租户管理 · 开通",       null),
                new PermissionDef("ops.tenant.update",         "租户管理 · 编辑",       null),
                new PermissionDef("ops.tenant.impersonate",    "租户管理 · 代登录",     null),
                new PermissionDef("ops.tenant.suspend",        "租户管理 · 暂停/启用",  null),
                new PermissionDef("ops.tenant.reset_password", "租户管理 · 重置主管密码", null),
                new PermissionDef("ops.tenant.contact.reveal", "租户管理 · 查看成员完整联系方式", "解密查看租户成员/主管理员完整手机 / 邮箱（敏感操作，记审计）"),

                // 套餐管理
                new PermissionDef("ops.package.list",    "套餐 · 列表",     null),
                new PermissionDef("ops.package.create",  "套餐 · 新建",     null),
                new PermissionDef("ops.package.update",  "套餐 · 编辑",     null),
                new PermissionDef("ops.package.publish", "套餐 · 上下架",   null),
                new PermissionDef("ops.package.archive", "套餐 · 归档",     null),
                new PermissionDef("ops.package.delete",  "套餐 · 删除",     null),

                // 推荐角色管理（套餐与功能子页）
                new PermissionDef("ops.recommended_role.list",    "推荐角色 · 列表",       null),
                new PermissionDef("ops.recommended_role.create",  "推荐角色 · 新建",       null),
                new PermissionDef("ops.recommended_role.update",  "推荐角色 · 编辑",       null),
                new PermissionDef("ops.recommended_role.publish", "推荐角色 · 发布/下架",  null),
                new PermissionDef("ops.recommended_role.delete",  "推荐角色 · 删除",       null),
                new PermissionDef("ops.recommended_role.copy",    "推荐角色 · 复制",       null),

                // 通知模板管理（平台默认模板 tenant_id=0）
                new PermissionDef("ops.notification_template.list",           "通知模板 · 列表",      null),
                new PermissionDef("ops.notification_template.create",         "通知模板 · 新建",      null),
                new PermissionDef("ops.notification_template.update",         "通知模板 · 编辑",      null),
                new PermissionDef("ops.notification_template.enable_disable", "通知模板 · 启用/停用", null),
                new PermissionDef("ops.notification_template.delete",         "通知模板 · 删除",      null),

                // 工单管理
                new PermissionDef("ops.ticket.view",   "工单 · 查看",   "工单列表 / 详情（只读）"),
                new PermissionDef("ops.ticket.handle", "工单 · 处理",   "认领 / 回复 / 转单 / 调整优先级 / 标记已解决 / 关闭"),
                new PermissionDef("ops.ticket.reopen", "工单 · 强制重启", "强制重启已关闭工单（仅超管）"),

                // 数据隐私与合规（跨租户审计只读）
                new PermissionDef("ops.compliance.view", "数据合规 · 审计查看",
                        "跨租户查看超量导出审批记录与合规事件（按 dataScope 收敛）"),

                // 业务模块管理（注册表内省只读）
                new PermissionDef("ops.module.view", "业务模块 · 查看",
                        "查看已注册业务模块清单 / 贡献能力 / 关联套餐（只读）")
        );
    }
}
