package com.wensaas.core.web.ops.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 菜单管理 · 移动 / 排序请求（{@code PUT /api/v1/ops/menus/{id}/move}）。
 *
 * @param parentKey 新父节点 node_key；移到顶层为 null
 * @param sort      同级排序号
 */
public record MenuMoveRequest(String parentKey, @NotNull Integer sort) {
}
