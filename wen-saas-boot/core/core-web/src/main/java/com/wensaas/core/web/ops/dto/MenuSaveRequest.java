package com.wensaas.core.web.ops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 菜单管理 · 新建请求（{@code POST /api/v1/ops/menus}）。
 *
 * @param scope          端：1=OPS / 2=TENANT / 3=H5
 * @param parentKey      父节点 node_key；顶层为 null
 * @param nodeType       1=GROUP / 2=PAGE / 3=ACTION
 * @param label          中文显示名
 * @param icon           图标名（GROUP/PAGE）
 * @param path           PAGE 路由
 * @param permissionCode PAGE 的 gate 码 / ACTION 的码
 * @param description    说明
 * @param sort           同级排序
 */
public record MenuSaveRequest(
        @NotNull Integer scope,
        String parentKey,
        @NotNull Integer nodeType,
        @NotBlank String label,
        String icon,
        String path,
        String permissionCode,
        String description,
        Integer sort) {
}
