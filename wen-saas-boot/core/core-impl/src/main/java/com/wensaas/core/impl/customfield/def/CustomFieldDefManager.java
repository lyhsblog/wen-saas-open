package com.wensaas.core.impl.customfield.def;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wensaas.core.api.customfield.CustomFieldException;
import com.wensaas.core.api.customfield.CustomFieldType;
import com.wensaas.core.api.spi.def.CustomFieldObjectDef;
import com.wensaas.core.impl.namesource.EntitySearchService;
import com.wensaas.core.impl.spi.registry.CustomFieldObjectRegistry;
import com.wensaas.core.impl.tenant.isolation.IgnoreStrategyScopes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import com.wensaas.core.impl.customfield.quota.CustomFieldQuotaManager;
import com.wensaas.core.impl.customfield.resolver.CustomFieldResolver;

/**
 * 自定义字段定义的内部写 API。v1 不暴露 REST，集成测试 + 未来 REST 控制器都直接调用本服务。
 *
 * <p>所有方法 {@link Transactional}：DDL（CREATE/DROP INDEX）与元数据 INSERT/UPDATE/DELETE 处在同一事务，
 * 任一步失败整体回滚。PostgreSQL 的 DDL 是事务性的，与 InnoDB 不同。
 */
public class CustomFieldDefManager {

    private static final Logger log = LoggerFactory.getLogger(CustomFieldDefManager.class);

    private final SysCustomFieldDefMapper defMapper;
    private final CustomFieldQuotaManager quotaManager;
    private final CustomFieldIndexExecutor indexExecutor;
    private final CustomFieldObjectRegistry objectRegistry;
    private final CustomFieldResolver resolver;
    private final CustomFieldValueValidator valueValidator;
    private final EntitySearchService entitySearchService;

    public CustomFieldDefManager(SysCustomFieldDefMapper defMapper,
                                  CustomFieldQuotaManager quotaManager,
                                  CustomFieldIndexExecutor indexExecutor,
                                  CustomFieldObjectRegistry objectRegistry,
                                  CustomFieldResolver resolver,
                                  CustomFieldValueValidator valueValidator,
                                  EntitySearchService entitySearchService) {
        this.defMapper = defMapper;
        this.quotaManager = quotaManager;
        this.indexExecutor = indexExecutor;
        this.objectRegistry = objectRegistry;
        this.resolver = resolver;
        this.valueValidator = valueValidator;
        this.entitySearchService = entitySearchService;
    }

    /**
     * 创建字段。
     *
     * <p>校验顺序：fieldCode 标识符规范 → 对象已注册 → 类型相关必填字段 → 字段 code 不重复 →
     * 字段额度 → 索引额度 → INSERT → DDL → evict 缓存。
     */
    @Transactional
    public Long createField(CreateFieldRequest req) {
        CustomFieldIndexExecutor.requireIdentifier("fieldCode", req.fieldCode());
        if (!req.fieldCode().startsWith("cf_")) {
            throw new CustomFieldException(CustomFieldException.Code.INVALID_FIELD_CODE_PREFIX,
                    "fieldCode 必须以 'cf_' 前缀开头（v2 约束），实际: "
                            + req.fieldCode());
        }
        CustomFieldObjectDef object = objectRegistry.findByKey(req.objectCode())
                .orElseThrow(() -> new CustomFieldException(
                        CustomFieldException.Code.OBJECT_NOT_REGISTERED,
                        "业务对象未注册: " + req.objectCode()));
        checkTypeSpecificRequired(req);

        SysCustomFieldDef existing = defMapper.findByTenantObjectAndCode(
                req.tenantId(), req.objectCode(), req.fieldCode());
        if (existing != null) {
            throw new CustomFieldException(CustomFieldException.Code.DUPLICATE_FIELD_CODE,
                    "字段 code 已存在: " + req.fieldCode() + "（object=" + req.objectCode() + "）");
        }

        quotaManager.incFieldUsageOrThrow(req.tenantId());
        if (req.indexed()) {
            quotaManager.incIndexUsageOrThrow(req.tenantId());
        }

        long id = IdWorker.getId();
        SysCustomFieldDef row = new SysCustomFieldDef();
        row.setId(id);
        row.setTenantId(req.tenantId());
        row.setObjectCode(req.objectCode());
        row.setFieldCode(req.fieldCode());
        row.setFieldName(req.fieldName());
        row.setFieldType(req.type().code());
        row.setIsRequired(req.required());
        row.setIsIndexed(req.indexed());
        row.setDefaultValue(req.defaultValue());
        row.setRefDictCode(req.refDictCode());
        row.setRefObjectCode(req.refObjectCode());
        row.setDisplayOrder(req.displayOrder());
        defMapper.insert(row);

        if (req.indexed()) {
            indexExecutor.createIndex(object.tableName(), object.extraColumnName(), req.fieldCode());
        }

        evictAround(req.tenantId(), req.objectCode());
        log.info("[CustomField] 创建字段 tenant={} object={} field={} type={} indexed={}",
                req.tenantId(), req.objectCode(), req.fieldCode(), req.type(), req.indexed());
        return id;
    }

    /** 改字段显示名。其它不可改。 */
    @Transactional
    public void renameField(long tenantId, long fieldId, String newName) {
        SysCustomFieldDef row = requireOwnField(tenantId, fieldId);
        row.setFieldName(newName);
        row.setUpdatedAt(LocalDateTime.now());
        defMapper.updateById(row);
        evictAround(tenantId, row.getObjectCode());
        log.info("[CustomField] 改名 tenant={} fieldId={} → {}", tenantId, fieldId, newName);
    }

    /** 软删字段。索引立即删除 + 释放索引额度；字段额度也释放。JSON key 不动（90 天后由清理 Job 移除）。 */
    @Transactional
    public void dropField(long tenantId, long fieldId) {
        SysCustomFieldDef row = requireOwnField(tenantId, fieldId);
        if (Boolean.TRUE.equals(row.getIsIndexed())) {
            CustomFieldObjectDef object = requireRegisteredObject(row.getObjectCode());
            dropSharedIndexIfUnused(object, row);
            quotaManager.decIndexUsage(tenantId);
        }
        defMapper.deleteById(row.getId()); // MP @TableLogic 会写 deleted_at
        quotaManager.decFieldUsage(tenantId);
        evictAround(tenantId, row.getObjectCode());
        log.info("[CustomField] 软删字段 tenant={} fieldId={} (field={})",
                tenantId, fieldId, row.getFieldCode());
    }

    /** 索引化双向切换。 */
    @Transactional
    public void toggleIndex(long tenantId, long fieldId, boolean newIndexed) {
        SysCustomFieldDef row = requireOwnField(tenantId, fieldId);
        boolean oldIndexed = Boolean.TRUE.equals(row.getIsIndexed());
        if (oldIndexed == newIndexed) {
            return;
        }
        CustomFieldObjectDef object = requireRegisteredObject(row.getObjectCode());
        if (newIndexed) {
            quotaManager.incIndexUsageOrThrow(tenantId);
            indexExecutor.createIndex(object.tableName(), object.extraColumnName(), row.getFieldCode());
        } else {
            dropSharedIndexIfUnused(object, row);
            quotaManager.decIndexUsage(tenantId);
        }
        row.setIsIndexed(newIndexed);
        row.setUpdatedAt(LocalDateTime.now());
        defMapper.updateById(row);
        evictAround(tenantId, row.getObjectCode());
        log.info("[CustomField] 索引化切换 tenant={} fieldId={} {} → {}",
                tenantId, fieldId, oldIndexed, newIndexed);
    }

    private void checkTypeSpecificRequired(CreateFieldRequest req) {
        if (req.type() == CustomFieldType.ENUM) {
            if (req.refDictCode() == null || req.refDictCode().isBlank()) {
                throw new CustomFieldException(CustomFieldException.Code.MISSING_REF_CODE,
                        "ENUM 类型必须填 refDictCode");
            }
            valueValidator.ensureDictGroupExists(req.refDictCode());
        }
        if (req.type() == CustomFieldType.REF) {
            if (req.refObjectCode() == null || req.refObjectCode().isBlank()) {
                throw new CustomFieldException(CustomFieldException.Code.MISSING_REF_CODE,
                        "REF 类型必须填 refObjectCode");
            }
            // refObjectCode 必须是合法 ref 目标 = 注册了 NameSearch 且 tenantSearchable && referenceable
            // 的实体（与字段管理「关联对象」下拉同一单一源），挡掉角色 / platform-only / 未注册等坏目标。
            if (!entitySearchService.isTenantReferenceable(req.refObjectCode())) {
                throw new CustomFieldException(CustomFieldException.Code.MISSING_REF_CODE,
                        "关联对象不合法（须为可引用的数据实体，如成员/部门/业务对象）: " + req.refObjectCode());
            }
        }
    }

    private SysCustomFieldDef requireOwnField(long tenantId, long fieldId) {
        SysCustomFieldDef row = defMapper.selectById(fieldId);
        if (row == null || row.getDeletedAt() != null || !row.getTenantId().equals(tenantId)) {
            throw new CustomFieldException(CustomFieldException.Code.FIELD_NOT_FOUND,
                    "字段不存在或不属于当前租户: " + fieldId);
        }
        return row;
    }

    /**
     * 物理删除共享表达式索引——仅当无其他租户仍对同一 {@code (object, field)} 索引化时才真正 DROP
     * （架构审查 M13）。
     *
     * <p>索引名 {@code idx_{table}_extra_{field}} 不含 tenant_id、跨租户共享（首个租户 CREATE、其余
     * {@code IF NOT EXISTS} 复用）。盲目 {@code DROP} 会误删其他租户仍在用的索引，使其查询静默退化全表扫。
     * 故 drop 前先跨租户计数：仍有他租户索引化（peers&gt;0）则保留共享索引，仅释放本租户额度；最后一个
     * 关闭的租户（peers==0）才执行物理 DROP。跨租户 COUNT 用窄作用域旁路 TenantLine（仅圈这一次查询）。
     *
     * <p>假设对象与物理表 1:1（每个注册 {@code CustomFieldObjectDef} 独占 tableName），故按
     * {@code (object_code, field_code)} 计数等价于按 {@code (table, field)} 判活。
     */
    private void dropSharedIndexIfUnused(CustomFieldObjectDef object, SysCustomFieldDef row) {
        long peers;
        try (var ignored = IgnoreStrategyScopes.enter(IgnoreStrategyScopes.TENANT_LINE)) {
            peers = defMapper.countOtherIndexedPeers(row.getObjectCode(), row.getFieldCode(), row.getId());
        }
        if (peers > 0) {
            log.info("[CustomField] 保留共享索引：object={} field={} 仍有 {} 个其他租户字段索引化，跳过物理 DROP",
                    row.getObjectCode(), row.getFieldCode(), peers);
            return;
        }
        indexExecutor.dropIndex(object.tableName(), row.getFieldCode());
    }

    private CustomFieldObjectDef requireRegisteredObject(String objectCode) {
        return objectRegistry.findByKey(objectCode)
                .orElseThrow(() -> new CustomFieldException(
                        CustomFieldException.Code.OBJECT_NOT_REGISTERED,
                        "业务对象未注册: " + objectCode));
    }

    private void evictAround(long tenantId, String objectCode) {
        resolver.evictDefs(tenantId, objectCode);
        resolver.evictQuota(tenantId);
    }
}
