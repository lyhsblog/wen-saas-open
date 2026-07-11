package com.wensaas.core.impl.namesource;

import com.wensaas.core.api.namesource.NameLookup;
import com.wensaas.core.api.namesource.NameSearch;
import com.wensaas.core.api.namesource.NameSearch.FieldMeta;
import com.wensaas.core.api.namesource.NameSearch.NameHit;
import com.wensaas.core.api.namesource.NameSearch.SearchQuery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 实体反向搜索的统一出口（租户上下文）。**通用能力，非审计专属**——
 * 经 {@link com.wensaas.core.web.tenant.TenantEntityLookupController} 对外，供审计「对象」筛选、
 * 自定义字段 ref 选择器、业务页面任意「选人/选部门/选关联实例」类筛选或表单字段共用。
 *
 * <p>聚合所有 {@link NameSearch} bean，对外提供两件事：
 * <ul>
 *   <li>{@link #tenantSearchableTypes()}：租户可搜索的实体类型元数据（{@code code/label/hasCode}），
 *   喂「对象类型」下拉与「选实例」弹窗的左侧类型列表（目前唯一消费者是审计「对象」筛选；其它消费者
 *   通常已知道自己要搜哪个固定 type，直接调 {@link #tenantSearch} 即可，不需要先列类型）；</li>
 *   <li>{@link #tenantSearch}：在某类型内精确搜候选实例。</li>
 * </ul>
 *
 * <p>安全：{@code tenantSearchable=false} 的类型（platform-only / 跨租户，如 {@code sys.tenant} /
 * {@code sys.package}）既不出现在类型列表、也拒绝被搜索（返回空，不泄露存在性），防租户越权。
 * label 取自同 code 的 {@link NameLookup#displayLabel()}（单一来源）；无对应 NameLookup 时回退 code。
 */
public class EntitySearchService {

    private final Map<String, NameSearch> searchByType;
    private final Map<String, String> labelByType;

    public EntitySearchService(List<NameSearch> searches, List<NameLookup> lookups) {
        Map<String, NameSearch> s = new LinkedHashMap<>();
        if (searches != null) {
            for (NameSearch ns : searches) {
                s.put(ns.entityType(), ns);
            }
        }
        Map<String, String> l = new LinkedHashMap<>();
        if (lookups != null) {
            for (NameLookup nl : lookups) {
                l.put(nl.entityType(), nl.displayLabel());
            }
        }
        this.searchByType = s;
        this.labelByType = l;
    }

    /** 租户可搜索类型元数据；排除 tenantSearchable=false 的类型。 */
    public List<EntityTypeMeta> tenantSearchableTypes() {
        List<EntityTypeMeta> out = new ArrayList<>();
        for (NameSearch ns : searchByType.values()) {
            if (!ns.tenantSearchable()) {
                continue;
            }
            out.add(new EntityTypeMeta(ns.entityType(), labelOf(ns.entityType()), ns.searchFields()));
        }
        return out;
    }

    /**
     * 租户端可作关联引用目标的实体类型 = {@code tenantSearchable && referenceable}（既能在租户端搜、又是
     * 数据记录实体）。供自定义字段 REF 目标下拉等使用；以 {@link NameSearch} 注册表为单一源，
     * 不另维护白名单——未注册 NameSearch 的实体本就搜不到 / 翻不出名，天然不在内。排除角色（referenceable=false）
     * 与 platform-only（tenantSearchable=false）。
     */
    public List<RefTypeMeta> tenantReferenceableTypes() {
        List<RefTypeMeta> out = new ArrayList<>();
        for (NameSearch ns : searchByType.values()) {
            if (ns.tenantSearchable() && ns.referenceable()) {
                out.add(new RefTypeMeta(ns.entityType(), labelOf(ns.entityType())));
            }
        }
        return out;
    }

    /** 某类型能否作租户端关联引用目标（注册 + tenantSearchable + referenceable）。 */
    public boolean isTenantReferenceable(String entityType) {
        NameSearch ns = searchByType.get(entityType);
        return ns != null && ns.tenantSearchable() && ns.referenceable();
    }

    /** 在某类型内按单关键字搜候选；未注册 / 不允许租户搜 → 空。 */
    public List<NameHit> tenantSearch(String entityType, String keyword, int limit, int offset) {
        NameSearch ns = searchByType.get(entityType);
        if (ns == null || !ns.tenantSearchable()) {
            return List.of();
        }
        return ns.search(new SearchQuery(keyword, limit, offset));
    }

    /** 兼容缺省 offset（=0）。 */
    public List<NameHit> tenantSearch(String entityType, String keyword, int limit) {
        return tenantSearch(entityType, keyword, limit, 0);
    }

    private String labelOf(String entityType) {
        String label = labelByType.get(entityType);
        return (label == null || label.isBlank()) ? entityType : label;
    }

    /**
     * 一个可搜索实体类型的展示元数据。{@code fields} 有序（第一个为主显），驱动前端提示文案 / 列；
     * {@code hasCode} 由「字段数 &gt; 1」派生（次字段即编码 / 辅助字段），供旧消费者沿用。
     */
    public record EntityTypeMeta(String code, String label, List<FieldMeta> fields) {
        public boolean hasCode() {
            return fields != null && fields.size() > 1;
        }
    }

    /** 一个可作关联引用目标的实体类型（code + 中文名）。 */
    public record RefTypeMeta(String code, String label) {}
}
