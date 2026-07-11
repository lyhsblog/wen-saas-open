package com.wensaas.core.impl.dict;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wensaas.core.api.dict.DictSource;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.api.spi.BusinessModuleDescriptor;
import com.wensaas.core.api.spi.def.DictExtraFieldDef;
import com.wensaas.core.api.spi.def.DictGroupDef;
import com.wensaas.core.api.spi.def.DictItemDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 把启动期发现的业务模块字典定义同步到 {@code sys_dict_group} / {@code sys_dict_item}。
 *
 * <p>策略（仅处理 MODULE 来源，tenant_id=0）：
 * <ul>
 *   <li>分组不存在 → INSERT 分组 + 一次性 INSERT 全部 {@code presetItems}（按 schema 校验，失败启动失败）</li>
 *   <li>分组已存在 → 比对 name / description / support_hierarchy / extra_schema，不同则 UPDATE
 *       （{@code supportHierarchy} 改了打 WARN 仍 UPDATE）</li>
 *   <li>已存在分组的 presetItems 重启不动（避免覆盖租户运行时变更）</li>
 *   <li>模块下线 → v1 不操作 DB（UI 阶段做"管理页隐藏 + 业务数据快照保留"）</li>
 * </ul>
 *
 * <p>整个 sync 由 {@link Transactional} 包裹，任一步失败回滚 + 启动失败。
 */
public class DictSpiSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(DictSpiSynchronizer.class);
    private static final int SOURCE_MODULE = DictSource.MODULE.code();
    private static final long PLATFORM_TENANT_ID = 0L;

    private final SysDictGroupMapper groupMapper;
    private final SysDictItemMapper itemMapper;
    private final DictExtraSchemaValidator schemaValidator;

    public DictSpiSynchronizer(SysDictGroupMapper groupMapper,
                                SysDictItemMapper itemMapper,
                                DictExtraSchemaValidator schemaValidator) {
        this.groupMapper = groupMapper;
        this.itemMapper = itemMapper;
        this.schemaValidator = schemaValidator;
    }

    @Transactional
    public void sync(List<BusinessModuleDescriptor> enabledModules) {
        // 只读写 tenant_id=0 的模板行；启动期无请求上下文，显式以平台租户身份执行，
        // 混合表（sys_dict_group / sys_dict_item）的租户线条件即命中 tenant_id=0。
        TenantContext.runAs(PLATFORM_TENANT_ID, () -> doSync(enabledModules));
    }

    private void doSync(List<BusinessModuleDescriptor> enabledModules) {
        int created = 0;
        int updated = 0;
        for (BusinessModuleDescriptor d : enabledModules) {
            String moduleCode = d.metadata().code();
            for (DictGroupDef def : d.dictGroups()) {
                if (syncOne(moduleCode, def)) {
                    created++;
                } else {
                    updated++;  // 也含"无变化"，仅用于统计 visited
                }
            }
        }
        log.info("[Dict-SPI] 已同步业务模块字典分组：新建 {}、命中 {}", created, updated);
    }

    /** @return true 表示新建分组；false 表示已存在（含 UPDATE 与 NoChange） */
    private boolean syncOne(String moduleCode, DictGroupDef def) {
        SysDictGroup existing = groupMapper.selectByCodeAndSource(
                PLATFORM_TENANT_ID, def.code(), SOURCE_MODULE);

        if (existing == null) {
            // 先校验 presetItems 的 extra 是否符合 schema（任意一项失败整个 sync 回滚）
            for (DictItemDef item : def.presetItems()) {
                schemaValidator.validate(def.extraSchema(), item.extra());
            }
            SysDictGroup row = new SysDictGroup();
            row.setId(IdWorker.getId());
            row.setTenantId(PLATFORM_TENANT_ID);
            row.setCode(def.code());
            row.setName(def.name());
            row.setDescription(def.description());
            row.setSource(SOURCE_MODULE);
            row.setModuleCode(moduleCode);
            row.setSupportHierarchy(def.supportHierarchy());
            row.setExtraSchema(def.extraSchema());
            row.setStatus(1);
            groupMapper.insert(row);
            insertPresetItems(def, row.getId());
            log.info("[Dict-SPI] 新增分组 {}（owner={}, items={}）",
                    def.code(), moduleCode, def.presetItems().size());
            return true;
        }

        boolean changed = false;
        if (!Objects.equals(existing.getName(), def.name())) {
            existing.setName(def.name()); changed = true;
        }
        if (!Objects.equals(existing.getDescription(), def.description())) {
            existing.setDescription(def.description()); changed = true;
        }
        if (!Objects.equals(existing.getSupportHierarchy(), def.supportHierarchy())) {
            log.warn("[Dict-SPI] 分组 {} supportHierarchy 从 {} 改为 {}（业务方自负数据后果）",
                    def.code(), existing.getSupportHierarchy(), def.supportHierarchy());
            existing.setSupportHierarchy(def.supportHierarchy()); changed = true;
        }
        List<DictExtraFieldDef> oldSchema = existing.getExtraSchema() == null ? List.of() : existing.getExtraSchema();
        List<DictExtraFieldDef> newSchema = def.extraSchema();
        if (!Objects.equals(oldSchema, newSchema)) {
            existing.setExtraSchema(newSchema); changed = true;
        }
        if (!Objects.equals(existing.getModuleCode(), moduleCode)) {
            existing.setModuleCode(moduleCode); changed = true;
        }
        if (changed) {
            groupMapper.updateById(existing);
            log.info("[Dict-SPI] 更新分组 schema {}（owner={}）", def.code(), moduleCode);
        }
        return false;
    }

    private void insertPresetItems(DictGroupDef def, long groupId) {
        if (def.presetItems().isEmpty()) {
            return;
        }
        // 第一遍按 parentCode==null 先插顶级，收集 code→id 映射；第二遍插子级
        Map<String, Long> codeToId = new HashMap<>();
        for (DictItemDef item : def.presetItems()) {
            if (item.parentCode() == null) {
                long id = IdWorker.getId();
                codeToId.put(item.code(), id);
                itemMapper.insert(toRow(def, item, id, 0L, "/"));
            }
        }
        for (DictItemDef item : def.presetItems()) {
            if (item.parentCode() != null) {
                Long parentId = codeToId.get(item.parentCode());
                if (parentId == null) {
                    throw new IllegalStateException("字典 " + def.code() + " 项 "
                            + item.code() + " 引用未声明的 parentCode=" + item.parentCode());
                }
                long id = IdWorker.getId();
                codeToId.put(item.code(), id);
                itemMapper.insert(toRow(def, item, id, parentId, "/" + parentId + "/"));
            }
        }
    }

    private static SysDictItem toRow(DictGroupDef def, DictItemDef item,
                                       long id, long parentId, String ancestors) {
        SysDictItem row = new SysDictItem();
        row.setId(id);
        row.setTenantId(PLATFORM_TENANT_ID);
        row.setGroupCode(def.code());
        row.setCode(item.code());
        row.setName(item.name());
        row.setParentId(parentId);
        row.setAncestors(ancestors);
        row.setSortOrder(item.sortOrder() == null ? 0 : item.sortOrder());
        row.setStatus(1);
        row.setSource(SOURCE_MODULE);
        row.setRemark(item.remark());
        row.setExtra(item.extra());
        return row;
    }
}
