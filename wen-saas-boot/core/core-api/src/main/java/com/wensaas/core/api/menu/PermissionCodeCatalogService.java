package com.wensaas.core.api.menu;

import java.util.List;

/**
 * 权限码目录（读）。按端 scope 列出后端「可授权限码」——菜单管理绑码时供选、保存时校验、角色目录
 * 计算 unbound 孤儿码。
 *
 * <p>权威来源：OPS 取 SPI 注册的 {@code ops.*}；TENANT 取功能权限目录（底座静态 + 业务模块 SPI，
 * 非 {@code ops.*}）。与权限码契约（{@code @RequirePermission} + SPI 声明）同源，菜单只引用、不新增码。
 */
public interface PermissionCodeCatalogService {

    /** 列出 scope 下全部可授权限码（{@link MenuQueryService#SCOPE_OPS} / {@code SCOPE_TENANT}）。 */
    List<PermissionCodeView> codes(int scope);

    /**
     * 可授权限码视图。
     *
     * @param code        权限码
     * @param name        显示名
     * @param description 说明，可空
     * @param scope       所属端
     * @param dataScoped  是否参与数据权限（{@code @DataPermission} 标注的 action）
     */
    record PermissionCodeView(String code, String name, String description, int scope, boolean dataScoped) {
    }
}
