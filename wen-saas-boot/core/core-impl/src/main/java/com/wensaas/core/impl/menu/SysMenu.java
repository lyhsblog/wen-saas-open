package com.wensaas.core.impl.menu;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * sys_menu 表实体。菜单树的唯一权威。
 *
 * <p>纯平台表（无 tenant_id，已在 wen-saas-boot.tenant.platform-only-tables 白名单）：菜单结构对所有租户
 * 一致，差异只在「角色持有哪些权限码」。后端代码不声明菜单——初始数据由 Flyway 一次性种子，此后由运营
 * 「菜单管理」页独占维护。后端只存储 + 提供，与权限码经 {@code permissionCode} 相连。
 *
 * <p>三种节点类型（{@link #nodeType}）：1=GROUP 分节容器（无 path/code）、2=PAGE 导航页（有 path、可选
 * gate 码）、3=ACTION 动作码（挂 PAGE 下，无 path）。可见性：无码 / 持码 / owner / 有可见子。
 */
@TableName("sys_menu")
@Getter
@Setter
public class SysMenu {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 端：1=OPS / 2=TENANT / 3=H5。 */
    private Integer scope;

    /** 稳定键，(scope, nodeKey) 唯一；种子幂等 + parentKey 引用锚。 */
    private String nodeKey;

    /** 树父（同 scope 的 nodeKey）；顶层为 null。 */
    private String parentKey;

    /** 1=GROUP / 2=PAGE / 3=ACTION。 */
    private Integer nodeType;

    /** 中文显示名。 */
    private String label;

    /** GROUP/PAGE 图标名（antd/lucide 字符串，shell 解析）。 */
    private String icon;

    /** PAGE 相对路由（不含 /:tenantKey）；GROUP/ACTION 为 null。 */
    private String path;

    /** PAGE 的 gate 码 / ACTION 的码；GROUP 恒 null；null 的 PAGE 对所有人可见、不作勾选叶子。 */
    private String permissionCode;

    /** ACTION 说明（角色页 tooltip）。 */
    private String description;

    /** 同级排序。 */
    private Integer sort;

    /** 1=SEED / 2=ADMIN（ADMIN 可删，SEED 仅可停用）。 */
    private Integer source;

    /** 1=ENABLED / 0=DISABLED。 */
    private Integer status;

    /** 启动校验置位：permissionCode 不在后端目录（悬空失效），菜单管理红标。 */
    private Boolean dangling;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
