package com.wensaas.core.impl.dict;

import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.wensaas.core.api.audit.AuditObjects;
import com.wensaas.core.api.dict.DictAdminException;
import com.wensaas.core.api.dict.DictAdminService;
import com.wensaas.core.api.dict.DictGroup;
import com.wensaas.core.api.dict.DictItem;
import com.wensaas.core.api.dict.DictSource;
import com.wensaas.core.api.spi.def.DictExtraFieldDef;
import com.wensaas.core.api.tenant.TenantContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.wensaas.core.impl.dict.resolver.DictResolver;

/**
 * 字典管理（写）实现。
 *
 * <p>租户上下文：所有写路径必须有 {@link TenantContext}；CUSTOM 写入 {@code tenant_id = TenantContext.require()}。
 *
 * <p>SYSTEM / MODULE 来源记录（tenant_id=0）由 SPI 同步 / Flyway 写入；本类只允许"追加 CUSTOM 项 + 改 name"，
 * 不允许删/改 schema。
 *
 * <p>每次写入后调 {@link DictResolver#evict} 清当前租户该分组缓存。
 */
public class DictAdminServiceImpl implements DictAdminService {

    private static final IgnoreStrategy IGNORE_TENANT_LINE =
            IgnoreStrategy.builder().tenantLine(true).build();

    /** 项层级最大深度（含根项）。 */
    private static final int MAX_DEPTH = 5;

    private final SysDictGroupMapper groupMapper;
    private final SysDictItemMapper itemMapper;
    private final DictExtraSchemaValidator validator;
    private final DictResolver resolver;

    public DictAdminServiceImpl(SysDictGroupMapper groupMapper,
                                 SysDictItemMapper itemMapper,
                                 DictExtraSchemaValidator validator,
                                 DictResolver resolver) {
        this.groupMapper = groupMapper;
        this.itemMapper = itemMapper;
        this.validator = validator;
        this.resolver = resolver;
    }

    // ===================== 分组 =====================

    @Override
    public List<DictGroup> listGroups() {
        long tenantId = TenantContext.require();
        List<SysDictGroup> rows = withTenantLineIgnored(() ->
                groupMapper.selectAllForTenant(tenantId));
        List<DictGroup> out = new ArrayList<>(rows.size());
        for (SysDictGroup r : rows) {
            out.add(toApi(r));
        }
        return out;
    }

    @Override
    @Transactional
    public DictGroup createGroup(CreateGroupRequest req) {
        requireNonBlank("code", req.code());
        requireNonBlank("name", req.name());
        long tenantId = TenantContext.require();
        SysDictGroup existing = withTenantLineIgnored(() ->
                groupMapper.selectForTenant(req.code(), tenantId));
        if (existing != null) {
            throw new DictAdminException(DictAdminException.Code.GROUP_CODE_DUPLICATE,
                    "分组编码已存在: " + req.code());
        }
        SysDictGroup row = new SysDictGroup();
        row.setTenantId(tenantId);
        row.setCode(req.code());
        row.setName(req.name());
        row.setDescription(req.description());
        row.setSource(DictSource.CUSTOM.code());
        row.setSupportHierarchy(req.supportHierarchy());
        row.setExtraSchema(req.extraSchema() == null ? List.of() : req.extraSchema());
        row.setStatus(1);
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        groupMapper.insert(row);
        resolver.evict(tenantId, req.code());
        return toApi(row);
    }

    @Override
    @Transactional
    public DictGroup updateGroup(String groupCode, UpdateGroupRequest req) {
        long tenantId = TenantContext.require();
        SysDictGroup row = loadGroupForWrite(tenantId, groupCode);
        // ② 审计对象名：加载后即登记「旧名/新名」（业务前，失败也留意图）
        AuditObjects.add(null, groupCode, row.getName(), req.name());
        boolean isCustom = row.getSource() == DictSource.CUSTOM.code();
        if (req.name() != null) {
            requireNonBlank("name", req.name());
            row.setName(req.name());
        }
        if (req.description() != null) {
            row.setDescription(req.description());
        }
        if (req.extraSchema() != null) {
            if (!isCustom) {
                throw new DictAdminException(
                        row.getSource() == DictSource.SYSTEM.code()
                                ? DictAdminException.Code.GROUP_SYSTEM_READONLY
                                : DictAdminException.Code.GROUP_MODULE_READONLY,
                        "仅 CUSTOM 分组允许修改扩展字段 schema");
            }
            row.setExtraSchema(req.extraSchema());
        }
        row.setUpdatedAt(LocalDateTime.now());
        groupMapper.updateById(row);
        resolver.evict(tenantId, groupCode);
        return toApi(row);
    }

    @Override
    @Transactional
    public void deleteGroup(String groupCode) {
        long tenantId = TenantContext.require();
        SysDictGroup row = loadGroupForWrite(tenantId, groupCode);
        // ② 审计对象名：加载后即登记当前名（业务前，失败也留意图）
        AuditObjects.add(null, groupCode, row.getName());
        if (row.getSource() != DictSource.CUSTOM.code()) {
            throw new DictAdminException(
                    row.getSource() == DictSource.SYSTEM.code()
                            ? DictAdminException.Code.GROUP_SYSTEM_READONLY
                            : DictAdminException.Code.GROUP_MODULE_READONLY,
                    "仅 CUSTOM 分组可删除");
        }
        int itemCount = itemMapper.countByTenantAndGroup(tenantId, groupCode);
        if (itemCount > 0) {
            throw new DictAdminException(DictAdminException.Code.GROUP_HAS_ITEMS,
                    "分组下尚有 " + itemCount + " 项，请先清空");
        }
        groupMapper.deleteById(row.getId()); // @TableLogic 软删
        resolver.evict(tenantId, groupCode);
    }

    @Override
    @Transactional
    public DictGroup setGroupStatus(String groupCode, boolean enabled) {
        long tenantId = TenantContext.require();
        SysDictGroup row = loadGroupForWrite(tenantId, groupCode);
        row.setStatus(enabled ? 1 : 0);
        row.setUpdatedAt(LocalDateTime.now());
        groupMapper.updateById(row);
        resolver.evict(tenantId, groupCode);
        return toApi(row);
    }

    // ===================== 项 =====================

    @Override
    public List<DictItem> listItemsForAdmin(String groupCode, boolean includeDisabled) {
        long tenantId = TenantContext.require();
        SysDictGroup group = withTenantLineIgnored(() ->
                groupMapper.selectForTenant(groupCode, tenantId));
        if (group == null) {
            throw new DictAdminException(DictAdminException.Code.GROUP_NOT_FOUND,
                    "分组不存在: " + groupCode);
        }
        List<SysDictItem> rows = withTenantLineIgnored(() ->
                itemMapper.selectAllForTenant(groupCode, tenantId));
        if (!includeDisabled) {
            rows.removeIf(r -> r.getStatus() != null && r.getStatus() == 0);
        }
        boolean hierarchical = Boolean.TRUE.equals(group.getSupportHierarchy());
        return hierarchical ? buildTree(rows) : flat(rows);
    }

    @Override
    @Transactional
    public DictItem createItem(String groupCode, CreateItemRequest req) {
        requireNonBlank("code", req.code());
        requireNonBlank("name", req.name());
        long tenantId = TenantContext.require();
        SysDictGroup group = withTenantLineIgnored(() ->
                groupMapper.selectForTenant(groupCode, tenantId));
        if (group == null) {
            throw new DictAdminException(DictAdminException.Code.GROUP_NOT_FOUND,
                    "分组不存在: " + groupCode);
        }
        validator.validate(group.getExtraSchema(), req.extra());

        // 同租户内项编码唯一性
        SysDictItem dup = itemMapper.selectByCode(tenantId, groupCode, req.code());
        if (dup != null) {
            throw new DictAdminException(DictAdminException.Code.ITEM_CODE_DUPLICATE,
                    "项编码已存在: " + req.code());
        }

        boolean hierarchical = Boolean.TRUE.equals(group.getSupportHierarchy());
        String ancestors = "/";
        Long parentId = req.parentId();
        if (parentId != null && parentId > 0) {
            if (!hierarchical) {
                throw new DictAdminException(DictAdminException.Code.ITEM_PARENT_INVALID,
                        "非层级分组不可指定 parentId");
            }
            SysDictItem parent = itemMapper.selectById(parentId);
            if (parent == null || !groupCode.equals(parent.getGroupCode())) {
                throw new DictAdminException(DictAdminException.Code.ITEM_PARENT_INVALID,
                        "父项不存在或跨分组: " + parentId);
            }
            // 深度校验：parent.ancestors 形如 /id1/id2/，当前层级 = '/' 计数
            int parentDepth = (int) parent.getAncestors().chars().filter(c -> c == '/').count() - 1;
            if (parentDepth + 1 >= MAX_DEPTH) {
                throw new DictAdminException(DictAdminException.Code.ITEM_DEPTH_EXCEEDED,
                        "层级深度超过 " + MAX_DEPTH);
            }
            ancestors = parent.getAncestors() + parent.getId() + "/";
        } else {
            parentId = 0L;
        }

        SysDictItem row = new SysDictItem();
        row.setTenantId(tenantId);
        row.setGroupCode(groupCode);
        row.setCode(req.code());
        row.setName(req.name());
        row.setParentId(parentId);
        row.setAncestors(ancestors);
        row.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        row.setStatus(1);
        row.setSource(DictSource.CUSTOM.code());
        row.setRemark(req.remark());
        row.setExtra(req.extra() == null ? Map.of() : req.extra());
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        itemMapper.insert(row);
        resolver.evict(tenantId, groupCode);
        return itemToApi(row);
    }

    @Override
    @Transactional
    public DictItem updateItem(long itemId, UpdateItemRequest req) {
        long tenantId = TenantContext.require();
        SysDictItem row = loadItemForWrite(tenantId, itemId);
        // ② 审计对象名：加载后即登记「旧名/新名」（业务前，失败也留意图）
        AuditObjects.add(null, String.valueOf(itemId), row.getName(), req.name());
        SysDictGroup group = withTenantLineIgnored(() ->
                groupMapper.selectForTenant(row.getGroupCode(), tenantId));
        if (req.name() != null) {
            requireNonBlank("name", req.name());
            row.setName(req.name());
        }
        if (req.sortOrder() != null) {
            row.setSortOrder(req.sortOrder());
        }
        if (req.remark() != null) {
            row.setRemark(req.remark());
        }
        if (req.extra() != null) {
            validator.validate(group == null ? List.of() : group.getExtraSchema(), req.extra());
            row.setExtra(req.extra());
        }
        row.setUpdatedAt(LocalDateTime.now());
        itemMapper.updateById(row);
        resolver.evict(tenantId, row.getGroupCode());
        return itemToApi(row);
    }

    @Override
    @Transactional
    public void deleteItem(long itemId) {
        long tenantId = TenantContext.require();
        SysDictItem row = loadItemForWrite(tenantId, itemId);
        // ② 审计对象名：加载后即登记当前名（业务前，失败也留意图）
        AuditObjects.add(null, String.valueOf(itemId), row.getName());
        if (row.getSource() != DictSource.CUSTOM.code()) {
            throw new DictAdminException(DictAdminException.Code.ITEM_SYSTEM_READONLY,
                    "仅 CUSTOM 项可删除（SYSTEM/MODULE 项仅可停用）");
        }
        int childCount = itemMapper.countChildren(tenantId, itemId);
        if (childCount > 0) {
            throw new DictAdminException(DictAdminException.Code.ITEM_HAS_CHILDREN,
                    "项下有 " + childCount + " 个子项，请先删除");
        }
        itemMapper.deleteById(itemId);
        resolver.evict(tenantId, row.getGroupCode());
    }

    @Override
    @Transactional
    public DictItem setItemStatus(long itemId, boolean enabled) {
        long tenantId = TenantContext.require();
        SysDictItem row = loadItemForWrite(tenantId, itemId);
        row.setStatus(enabled ? 1 : 0);
        row.setUpdatedAt(LocalDateTime.now());
        itemMapper.updateById(row);
        resolver.evict(tenantId, row.getGroupCode());
        return itemToApi(row);
    }

    // ===================== 内部工具 =====================

    /**
     * 加载可写分组：CUSTOM 必须 tenant 匹配；SYSTEM/MODULE 仅允许改 name/desc。
     * 不存在 → GROUP_NOT_FOUND。
     */
    private SysDictGroup loadGroupForWrite(long tenantId, String groupCode) {
        SysDictGroup row = withTenantLineIgnored(() ->
                groupMapper.selectForTenant(groupCode, tenantId));
        if (row == null) {
            throw new DictAdminException(DictAdminException.Code.GROUP_NOT_FOUND,
                    "分组不存在: " + groupCode);
        }
        return row;
    }

    /**
     * 加载可写项：要么 tenant_id=0（SYSTEM/MODULE）要么当前 tenant；跨租户 → ITEM_NOT_FOUND。
     */
    private SysDictItem loadItemForWrite(long tenantId, long itemId) {
        // 直接 selectById：MP TenantLine 会注入 tenant_id = X，命中不到 tenant_id=0 的预置项；
        // 这里走 ignore + 手工断言。
        SysDictItem row = withTenantLineIgnored(() -> itemMapper.selectById(itemId));
        if (row == null || (row.getTenantId() != 0L && row.getTenantId() != tenantId)) {
            throw new DictAdminException(DictAdminException.Code.ITEM_NOT_FOUND,
                    "项不存在: " + itemId);
        }
        return row;
    }

    private static <T> T withTenantLineIgnored(java.util.function.Supplier<T> action) {
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            return action.get();
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    private static DictGroup toApi(SysDictGroup r) {
        return new DictGroup(
                r.getCode(),
                r.getName(),
                r.getDescription(),
                DictSource.fromCode(r.getSource()),
                Boolean.TRUE.equals(r.getSupportHierarchy()),
                r.getExtraSchema() == null ? List.of() : r.getExtraSchema());
    }

    private static DictItem itemToApi(SysDictItem r) {
        return new DictItem(
                r.getId(),
                r.getCode(),
                r.getName(),
                null,
                r.getSortOrder() == null ? 0 : r.getSortOrder(),
                r.getStatus() == null ? 1 : r.getStatus(),
                r.getRemark(),
                DictSource.fromCode(r.getSource()),
                r.getExtra() == null ? Map.of() : r.getExtra(),
                List.of());
    }

    private static List<DictItem> flat(List<SysDictItem> rows) {
        List<DictItem> out = new ArrayList<>(rows.size());
        for (SysDictItem r : rows) {
            out.add(itemToApi(r));
        }
        return out;
    }

    private static List<DictItem> buildTree(List<SysDictItem> rows) {
        Map<Long, List<SysDictItem>> byParent = new HashMap<>();
        for (SysDictItem r : rows) {
            byParent.computeIfAbsent(r.getParentId() == null ? 0L : r.getParentId(),
                    k -> new ArrayList<>()).add(r);
        }
        return buildChildren(0L, byParent);
    }

    private static List<DictItem> buildChildren(long parentId, Map<Long, List<SysDictItem>> byParent) {
        return buildChildren(parentId, null, byParent);
    }

    private static List<DictItem> buildChildren(long parentId,
                                                String parentCode,
                                                Map<Long, List<SysDictItem>> byParent) {
        List<SysDictItem> children = byParent.get(parentId);
        if (children == null || children.isEmpty()) {
            return List.of();
        }
        List<DictItem> out = new ArrayList<>(children.size());
        for (SysDictItem c : children) {
            List<DictItem> sub = buildChildren(c.getId(), c.getCode(), byParent);
            out.add(new DictItem(
                    c.getId(),
                    c.getCode(),
                    c.getName(),
                    parentCode,
                    c.getSortOrder() == null ? 0 : c.getSortOrder(),
                    c.getStatus() == null ? 1 : c.getStatus(),
                    c.getRemark(),
                    DictSource.fromCode(c.getSource()),
                    c.getExtra() == null ? Map.of() : c.getExtra(),
                    sub));
        }
        return out;
    }

    private static void requireNonBlank(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }
}
