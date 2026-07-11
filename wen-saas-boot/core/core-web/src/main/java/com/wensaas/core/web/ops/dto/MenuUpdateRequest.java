package com.wensaas.core.web.ops.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 菜单管理 · 改属性请求（{@code PUT /api/v1/ops/menus/{id}}）。node_type/scope 不可改、父节点改用 move。
 *
 * @param label          中文显示名
 * @param icon           图标名
 * @param path           PAGE 路由（GROUP/ACTION 应为空）
 * @param permissionCode 绑定权限码
 * @param description    说明
 * @param sort           同级排序
 */
public record MenuUpdateRequest(
        @NotBlank String label,
        String icon,
        String path,
        String permissionCode,
        String description,
        Integer sort) {
}
