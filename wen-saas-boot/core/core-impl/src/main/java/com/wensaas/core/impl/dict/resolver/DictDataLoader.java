package com.wensaas.core.impl.dict.resolver;

import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.wensaas.core.api.dict.DictGroup;
import com.wensaas.core.api.dict.DictItem;
import com.wensaas.core.api.dict.DictSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.wensaas.core.impl.dict.SysDictGroup;
import com.wensaas.core.impl.dict.SysDictGroupMapper;
import com.wensaas.core.impl.dict.SysDictItem;
import com.wensaas.core.impl.dict.SysDictItemMapper;

/**
 * 字典装载器。给定 {@code (tenantId, groupCode)} 装载分组定义 + 启用项列表（拼好树）。
 *
 * <p>查询整体绕过 MP TenantLine（手工拼 {@code tenant_id IN (0, ?)}），与权限解析读路径同模式。
 */
public class DictDataLoader {

    private static final IgnoreStrategy IGNORE_TENANT_LINE =
            IgnoreStrategy.builder().tenantLine(true).build();

    private final SysDictGroupMapper groupMapper;
    private final SysDictItemMapper itemMapper;

    public DictDataLoader(SysDictGroupMapper groupMapper, SysDictItemMapper itemMapper) {
        this.groupMapper = groupMapper;
        this.itemMapper = itemMapper;
    }

    /**
     * 装载某分组在租户视角下的完整快照（分组 + 项树）。分组不存在返回 {@link Optional#empty()}。
     */
    public Optional<DictGroupSnapshot> load(long tenantId, String groupCode) {
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            return loadInternal(tenantId, groupCode);
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    private Optional<DictGroupSnapshot> loadInternal(long tenantId, String groupCode) {
        SysDictGroup group = groupMapper.selectForTenant(groupCode, tenantId);
        if (group == null) {
            return Optional.empty();
        }
        DictGroup api = new DictGroup(
                group.getCode(),
                group.getName(),
                group.getDescription(),
                DictSource.fromCode(group.getSource()),
                Boolean.TRUE.equals(group.getSupportHierarchy()),
                group.getExtraSchema() == null ? List.of() : group.getExtraSchema());

        List<SysDictItem> rawItems = itemMapper.selectEnabledForTenant(groupCode, tenantId);
        List<DictItem> items = api.supportHierarchy()
                ? buildTree(rawItems)
                : flat(rawItems);
        return Optional.of(new DictGroupSnapshot(api, items));
    }

    /** 非层级分组：扁平列表（children 始终为空）。 */
    private static List<DictItem> flat(List<SysDictItem> rows) {
        List<DictItem> out = new ArrayList<>(rows.size());
        for (SysDictItem r : rows) {
            out.add(toApi(r, List.of()));
        }
        return out;
    }

    /** 层级分组：按 parent_id 拼树，返回根节点列表。 */
    private static List<DictItem> buildTree(List<SysDictItem> rows) {
        Map<Long, List<SysDictItem>> byParent = new HashMap<>();
        for (SysDictItem r : rows) {
            byParent.computeIfAbsent(r.getParentId(), k -> new ArrayList<>()).add(r);
        }
        Map<Long, String> idToCode = new HashMap<>();
        for (SysDictItem r : rows) {
            idToCode.put(r.getId(), r.getCode());
        }
        return buildChildren(0L, byParent, idToCode);
    }

    private static List<DictItem> buildChildren(long parentId,
                                                  Map<Long, List<SysDictItem>> byParent,
                                                  Map<Long, String> idToCode) {
        List<SysDictItem> children = byParent.get(parentId);
        if (children == null || children.isEmpty()) {
            return List.of();
        }
        List<DictItem> out = new ArrayList<>(children.size());
        for (SysDictItem c : children) {
            List<DictItem> sub = buildChildren(c.getId(), byParent, idToCode);
            // parent code（顶级 null；否则查 map）
            DictItem api = toApi(c, sub);
            out.add(api);
        }
        return out;
    }

    private static DictItem toApi(SysDictItem r, List<DictItem> children) {
        Map<String, Object> extra = r.getExtra() == null ? Map.of() : r.getExtra();
        String parentCode = null; // 非层级或顶级 → null；层级非顶级时由调用栈无法直接拿到父 code，
        // 当前 API 设计以 children 树形表达父子关系，parentCode 字段保留扩展位
        return new DictItem(
                r.getId(),
                r.getCode(),
                r.getName(),
                parentCode,
                r.getSortOrder() == null ? 0 : r.getSortOrder(),
                r.getStatus() == null ? 1 : r.getStatus(),
                r.getRemark(),
                DictSource.fromCode(r.getSource()),
                extra,
                children);
    }

    /** 装载结果。 */
    public record DictGroupSnapshot(DictGroup group, List<DictItem> items) {
    }
}
