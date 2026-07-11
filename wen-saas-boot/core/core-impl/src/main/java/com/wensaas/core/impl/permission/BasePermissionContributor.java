package com.wensaas.core.impl.permission;

import com.wensaas.core.api.spi.def.PermissionDef;
import com.wensaas.core.impl.spi.registry.PermissionRegistry;

import java.util.List;

/**
 * 底座租户域权限点贡献器。
 *
 * <p>底座租户能力的权限点（通讯录 / 角色 / 字典 / 公司信息 / 套餐账号 / 存储 / 通知模板 /
 * 审计 / 字段管理）由本 bean 注册到 {@link PermissionRegistry}，作为权限码代码权威的单一来源。
 * 菜单结构（分组 / 中文名 / 排序）以 {@code sys_menu} 为权威，二者只以权限码相连。
 *
 * <p>合规 / 工单权限点 {@code base.compliance.*} / {@code base.ticket.*} 由
 * {@code BaseBusinessModuleDescriptor} 注册（owner 同为 {@code base}），不在此重复声明。
 *
 * <p>构造期注册，早于读 {@link PermissionRegistry} 的 {@code SmartInitializingSingleton}
 * （{@code MenuPermissionConsistencyValidator}）与 {@code ApplicationRunner}，见 {@code OpsPermissionContributor}。
 */
public class BasePermissionContributor {

    /** 底座租户域权限点所有者键（非业务模块，仅作 PermissionRegistry owner 归属标识）。 */
    public static final String OWNER = "base";

    public BasePermissionContributor(PermissionRegistry permissionRegistry) {
        permissions().forEach(p -> permissionRegistry.register(OWNER, p));
    }

    /** 底座租户域权限点清单（与各控制器 {@code @RequirePermission} 对齐）。 */
    public static List<PermissionDef> permissions() {
        return List.of(
                // 通讯录 · 组织架构
                new PermissionDef("org.dept.list", "查看", "查看部门树与详情"),
                new PermissionDef("org.dept.create", "添加部门", null),
                new PermissionDef("org.dept.update", "编辑部门", "编辑 / 移动 / 启停部门"),
                new PermissionDef("org.dept.delete", "删除部门", null),
                new PermissionDef("org.dept.set_leader", "设置部门负责人", null),
                // 通讯录 · 成员管理
                new PermissionDef("org.member.list", "查看", "查看成员列表与详情"),
                new PermissionDef("org.member.contact.reveal", "查看完整联系方式",
                        "解密查看成员完整手机 / 邮箱（敏感操作，记审计）"),
                new PermissionDef("org.member.create", "添加成员", null),
                new PermissionDef("org.member.update", "编辑成员", null),
                new PermissionDef("org.member.assign_dept", "管理所属部门", "调整成员主部门 / 兼任部门"),
                new PermissionDef("org.member.toggle_status", "停用 / 启用", null),
                new PermissionDef("org.member.reset_password", "重置密码", "生成重置链接 / 解除登录锁定"),
                new PermissionDef("org.member.assign_role", "设置角色", null),

                // 系统管理 · 角色与权限
                new PermissionDef("role.list", "查看", "查看角色与权限配置"),
                new PermissionDef("role.create", "新建角色", null),
                new PermissionDef("role.update", "编辑角色", "改基本信息 / 功能权限 / 数据范围 / 成员"),
                new PermissionDef("role.delete", "删除角色", null),
                // 系统管理 · 字典管理
                new PermissionDef("dict.group.list", "查看字典分组", null),
                new PermissionDef("dict.group.create", "新建字典分组", null),
                new PermissionDef("dict.group.update", "编辑字典分组", null),
                new PermissionDef("dict.group.delete", "删除字典分组", null),
                new PermissionDef("dict.item.list", "查看字典项", null),
                new PermissionDef("dict.item.create", "新建字典项", null),
                new PermissionDef("dict.item.update", "编辑字典项", null),
                new PermissionDef("dict.item.delete", "删除字典项", null),
                // 系统管理 · 审计日志
                new PermissionDef("audit.view", "查看审计日志",
                        "查看本租户全员的操作审计日志（默认无，可赋予合规 / 安全等角色；主管理员恒有）"),
                // 系统管理 · 公司信息
                new PermissionDef("company.view", "查看", "查看公司信息与品牌定制"),
                new PermissionDef("company.edit", "编辑", "编辑行业 / 规模 / 备注 / Logo / 路由位"),
                // 系统管理 · 套餐与账号
                new PermissionDef("account.view", "查看", "查看套餐 / 席位 / 到期 / 变更历史"),
                // 系统管理 · 存储设置
                new PermissionDef("storage.view", "查看", "查看当前存储后端配置"),
                new PermissionDef("storage.edit", "编辑", "切换 / 配置自有 OSS（敏感）"),
                // 系统管理 · 通知模板
                new PermissionDef("notification_template.view", "查看", "查看通知模板与文案"),
                new PermissionDef("notification_template.edit", "编辑", "启停 / 渠道 / 优先级（文案只读）"),
                // 系统管理 · 字段管理
                new PermissionDef("custom_field.view", "查看", "查看自定义字段与额度"),
                new PermissionDef("custom_field.manage", "管理", "新增 / 改名 / 删除字段、索引化切换（主管理员恒有）"),
                // 系统管理 · Webhook 订阅
                new PermissionDef("webhook.view", "查看", "查看 Webhook 订阅与投递日志"),
                new PermissionDef("webhook.manage", "管理",
                        "新建 / 编辑 / 启停 / 删除订阅、轮换签名密钥、发送测试事件（敏感：回调体含业务负载）"),
                // 系统管理 · 开放 API 凭证
                new PermissionDef("api_key.view", "查看", "查看开放 API 凭证列表（不含 secret）"),
                new PermissionDef("api_key.manage", "管理",
                        "签发 / 编辑 / 启停凭证、轮换密钥（敏感：凭证可访问本租户开放 API 全量数据）")
        );
    }
}
