package com.wensaas.core.web.ops.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 菜单管理 · 启停请求（{@code PUT /api/v1/ops/menus/{id}/status}）。
 *
 * @param enabled true=启用 / false=停用
 */
public record MenuStatusRequest(@NotNull Boolean enabled) {
}
