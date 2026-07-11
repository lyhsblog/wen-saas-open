package com.wensaas.core.impl.customfield;

import com.wensaas.core.api.customfield.CustomFieldDef;
import com.wensaas.core.api.customfield.CustomFieldObject;
import com.wensaas.core.api.customfield.CustomFieldQuota;
import com.wensaas.core.api.customfield.CustomFieldRefObject;
import com.wensaas.core.api.customfield.CustomFieldService;
import com.wensaas.core.api.spi.def.CustomFieldObjectDef;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.namesource.EntityNameResolver;
import com.wensaas.core.impl.namesource.EntitySearchService;
import com.wensaas.core.impl.spi.registry.CustomFieldObjectRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.wensaas.core.impl.customfield.def.CustomFieldValueValidator;
import com.wensaas.core.impl.customfield.quota.CustomFieldQuotaManager;
import com.wensaas.core.impl.customfield.resolver.CustomFieldResolver;

/**
 * {@link CustomFieldService} 缺省实现。
 *
 * <p>所有读路径走 {@link CustomFieldResolver}（带 Redis 缓存）；
 * 租户上下文从 {@link TenantContext#require()} 取。
 */
public class CustomFieldServiceImpl implements CustomFieldService {

    private final CustomFieldObjectRegistry objectRegistry;
    private final CustomFieldResolver resolver;
    private final CustomFieldQuotaManager quotaManager;
    private final CustomFieldValueValidator valueValidator;
    private final CustomFieldProperties props;
    private final EntityNameResolver nameResolver;
    private final EntitySearchService entitySearchService;

    public CustomFieldServiceImpl(CustomFieldObjectRegistry objectRegistry,
                                    CustomFieldResolver resolver,
                                    CustomFieldQuotaManager quotaManager,
                                    CustomFieldValueValidator valueValidator,
                                    CustomFieldProperties props,
                                    EntityNameResolver nameResolver,
                                    EntitySearchService entitySearchService) {
        this.objectRegistry = objectRegistry;
        this.resolver = resolver;
        this.quotaManager = quotaManager;
        this.valueValidator = valueValidator;
        this.props = props;
        this.nameResolver = nameResolver;
        this.entitySearchService = entitySearchService;
    }

    @Override
    public List<CustomFieldObject> listObjects() {
        List<CustomFieldObjectDef> all = objectRegistry.getAll();
        List<CustomFieldObject> out = new ArrayList<>(all.size());
        for (CustomFieldObjectDef d : all) {
            // 类型显示名统一取自同 code 的 NameLookup.displayLabel()；无对应 NameLookup 时回退 code
            String label = nameResolver.displayLabel(d.code());
            out.add(new CustomFieldObject(d.code(), label == null ? d.code() : label,
                    d.tableName(), d.extraColumnName(), d.description()));
        }
        return out;
    }

    @Override
    public List<CustomFieldRefObject> listRefObjects() {
        // 单一源：可作关联引用目标的实体类型（注册了 NameSearch 且 tenantSearchable && referenceable）。
        return entitySearchService.tenantReferenceableTypes().stream()
                .map(t -> new CustomFieldRefObject(t.code(), t.label()))
                .toList();
    }

    @Override
    public List<CustomFieldDef> listFields(String objectCode) {
        return resolver.resolveDefs(TenantContext.require(), objectCode);
    }

    @Override
    public Optional<CustomFieldDef> getField(String objectCode, String fieldCode) {
        return listFields(objectCode).stream()
                .filter(d -> d.fieldCode().equals(fieldCode))
                .findFirst();
    }

    @Override
    public CustomFieldQuota getQuota() {
        long tenantId = TenantContext.require();
        // 通过 manager 走 init 路径，确保第一次访问的租户也有默认行可读
        quotaManager.getOrInit(tenantId);
        return resolver.resolveQuota(tenantId,
                props.getDefaultFieldQuota(), props.getDefaultIndexQuota());
    }

    @Override
    public void validateValues(String objectCode, Map<String, Object> values) {
        List<CustomFieldDef> defs = listFields(objectCode);
        valueValidator.validate(defs, values);
    }
}
