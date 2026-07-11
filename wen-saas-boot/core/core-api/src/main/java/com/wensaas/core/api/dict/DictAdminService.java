package com.wensaas.core.api.dict;

import com.wensaas.core.api.spi.def.DictExtraFieldDef;

import java.util.List;
import java.util.Map;

/**
 * 字典管理（写）API。
 *
 * <p>租户上下文：实现从 {@code TenantContext.require()} 取 {@code tenantId}。
 * 所有写路径仅作用于"自定义"（CUSTOM）分组与项；SYSTEM/MODULE 来源的预置内容受保护，不可增删改。
 *
 * <p>读路径仍走 {@link DictService}。
 */
public interface DictAdminService {

    // ===================== 分组 =====================

    /** 列出当前租户视角下的分组（合并 SYSTEM/MODULE + CUSTOM），按 source asc + code asc 排序。 */
    List<DictGroup> listGroups();

    /** 新建 CUSTOM 分组。code 同租户内唯一。 */
    DictGroup createGroup(CreateGroupRequest req);

    /** 改 CUSTOM 分组的 name/description/status；SYSTEM/MODULE 仅允许改 name/description（不能改 schema 与层级开关）。 */
    DictGroup updateGroup(String groupCode, UpdateGroupRequest req);

    /** 删除 CUSTOM 分组（且无项时）；SYSTEM/MODULE 不可删。 */
    void deleteGroup(String groupCode);

    /** 启用 / 停用分组。 */
    DictGroup setGroupStatus(String groupCode, boolean enabled);

    // ===================== 项 =====================

    /** 列分组下所有项的视图（含已停用，扁平或树形按分组 supportHierarchy 决定）。 */
    List<DictItem> listItemsForAdmin(String groupCode, boolean includeDisabled);

    /** 新建项。CUSTOM 分组：任意；SYSTEM/MODULE 分组：允许追加 CUSTOM 项。 */
    DictItem createItem(String groupCode, CreateItemRequest req);

    /** 改项的 name/sort/status/remark/extra；SYSTEM/MODULE 来源项可改但不可删。 */
    DictItem updateItem(long itemId, UpdateItemRequest req);

    /** 删除 CUSTOM 来源项（要求无子项）；SYSTEM/MODULE 来源项不可删，仅可停用。 */
    void deleteItem(long itemId);

    /** 启用 / 停用项。 */
    DictItem setItemStatus(long itemId, boolean enabled);

    // ===================== 请求 records =====================

    record CreateGroupRequest(
            String code,
            String name,
            String description,
            boolean supportHierarchy,
            List<DictExtraFieldDef> extraSchema) {
    }

    record UpdateGroupRequest(
            String name,
            String description,
            List<DictExtraFieldDef> extraSchema /* 仅 CUSTOM 生效 */) {
    }

    record CreateItemRequest(
            String code,
            String name,
            Long parentId,
            Integer sortOrder,
            String remark,
            Map<String, Object> extra) {
    }

    record UpdateItemRequest(
            String name,
            Integer sortOrder,
            String remark,
            Map<String, Object> extra) {
    }
}
