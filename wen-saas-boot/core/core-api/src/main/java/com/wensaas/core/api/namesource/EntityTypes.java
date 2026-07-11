package com.wensaas.core.api.namesource;

/**
 * 底座内建实体类型 code 常量（{@link NameLookup#entityType()} 取值）。
 *
 * <p>统一在此声明，避免各调用点散落字符串字面量拼错。业务模块的实体类型 code 自行定义（与
 * {@code CustomFieldObjectDef.code()} 一致），不必登记于此。
 *
 * <p>跨租户翻名不靠单独登记跨租户源：同一个普通源（如 {@link #SYS_USER}）由调用方在
 * {@code @TransField(scope = CROSS)} 上声明跨租户取向、{@code TransFiller} 就地旁路租户线即可。
 * 详见 {@link TenantScope}。
 */
public final class EntityTypes {

    private EntityTypes() {
    }

    /** 租户 {@code sys_tenant}（platform-only，无 code；本身不在租户线内，跨租户无需特殊处理）。 */
    public static final String SYS_TENANT = "sys.tenant";
    /**
     * 用户 {@code sys_user}（name=realName，无 code）。带租户线：默认 {@code @TransField(scope = FOLLOW)}
     * 给租户端翻本租户成员；运营端跨租户翻别租户成员时同一源走 {@code scope = CROSS}（见 {@link TenantScope}）。
     */
    public static final String SYS_USER = "sys.user";
    /** 部门 {@code sys_dept}（无 code）。 */
    public static final String SYS_DEPT = "sys.dept";
    /** 角色 {@code sys_role}（有 code）。 */
    public static final String SYS_ROLE = "sys.role";
    /** 套餐 {@code sys_package}（有 code）。 */
    public static final String SYS_PACKAGE = "sys.package";
}
