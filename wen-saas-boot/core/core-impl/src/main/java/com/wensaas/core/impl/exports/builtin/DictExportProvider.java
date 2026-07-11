package com.wensaas.core.impl.exports.builtin;

import com.wensaas.core.api.dict.DictAdminService;
import com.wensaas.core.api.dict.DictGroup;
import com.wensaas.core.api.dict.DictItem;
import com.wensaas.core.api.exports.spi.ExportColumn;
import com.wensaas.core.api.exports.spi.ExportDataProvider;
import com.wensaas.core.api.exports.spi.ExportSink;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 数据字典导出（导出对象 {@code dict}，与 {@code BaseExportObjectContributor} 声明对齐，
 * 默认<b>无需审批</b>——低敏对象，直接投任务执行）。层级字典平铺导出（父项在前、子项跟随）。
 *
 * <p>filters：{@code groupCode}（缺省导全部分组）/ {@code includeDisabled}（默认 true）。
 */
public class DictExportProvider implements ExportDataProvider {

    private final DictAdminService dictService;

    public DictExportProvider(DictAdminService dictService) {
        this.dictService = dictService;
    }

    @Override
    public String objectCode() {
        return "dict";
    }

    @Override
    public String requiredPermission() {
        return "dict.item.list";
    }

    @Override
    public List<ExportColumn> columns() {
        return List.of(
                new ExportColumn("groupCode", "分组编码"),
                new ExportColumn("groupName", "分组名称"),
                new ExportColumn("itemCode", "字典项编码"),
                new ExportColumn("itemName", "字典项名称"),
                new ExportColumn("parentCode", "父项编码"),
                new ExportColumn("sortOrder", "排序"),
                new ExportColumn("status", "状态"),
                new ExportColumn("source", "来源"));
    }

    @Override
    public long estimateCount(Map<String, String> filters) {
        long[] count = {0};
        forEachItem(filters, (group, item) -> count[0]++);
        return count[0];
    }

    @Override
    public void export(Map<String, String> filters, ExportSink sink) {
        forEachItem(filters, (group, item) -> {
            if (sink.stopped()) {
                return;
            }
            sink.row(Arrays.asList(
                    group.code(),
                    group.name(),
                    item.code(),
                    item.name(),
                    item.parentCode(),
                    String.valueOf(item.sortOrder()),
                    item.status() == 1 ? "启用" : "停用",
                    item.source() == null ? null : item.source().name()));
        });
    }

    // ===================== 内部 =====================

    private interface ItemVisitor {
        void visit(DictGroup group, DictItem item);
    }

    private void forEachItem(Map<String, String> filters, ItemVisitor visitor) {
        String groupCode = filters.get("groupCode");
        boolean includeDisabled = Boolean.parseBoolean(filters.getOrDefault("includeDisabled", "true"));
        List<DictGroup> groups = dictService.listGroups().stream()
                .filter(g -> groupCode == null || groupCode.isBlank() || g.code().equals(groupCode))
                .toList();
        for (DictGroup group : groups) {
            walk(group, dictService.listItemsForAdmin(group.code(), includeDisabled), visitor);
        }
    }

    /** 树平铺：父项在前、子项跟随（深度优先）。 */
    private void walk(DictGroup group, List<DictItem> items, ItemVisitor visitor) {
        for (DictItem item : items) {
            visitor.visit(group, item);
            if (!item.children().isEmpty()) {
                walk(group, item.children(), visitor);
            }
        }
    }
}
