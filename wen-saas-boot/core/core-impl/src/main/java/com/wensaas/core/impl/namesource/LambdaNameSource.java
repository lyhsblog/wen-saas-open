package com.wensaas.core.impl.namesource;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.wensaas.core.api.namesource.MatchMode;
import com.wensaas.core.api.namesource.NameLookup;
import com.wensaas.core.api.namesource.NameSearch;
import com.wensaas.core.api.namesource.RenderMode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于实体类 + 方法引用的 {@link NameLookup} + {@link NameSearch} 默认实现。
 *
 * <p>实体的可搜 / 可显字段建模成<b>有序字段列表</b>（{@link DisplayField}）：第一个为主显字段（{@code primary}），
 * 可选一个次字段（{@code secondary}）；每个字段带中文 label + {@link MatchMode}。展示串由 {@link RenderMode}
 * 决定（PRIMARY 仅主、BOTH 主带次）。一行注册即可，无需手写 SQL——经 {@code NameSourceFactory.source(...)} 构建。
 *
 * <p>用方法引用而非裸字符串列名：编译期校验、IDE 改名传播、零注入面；getter 用 {@link SFunction}（既能
 * {@code .apply()} 取值，又能被 MyBatis-Plus 反解列名进 WHERE，正向翻译与反向搜索共用一套注册）。租户过滤由
 * {@code TenantLineHandlerImpl} 按 {@code platform-only-tables} 白名单自动接管；platform-only / 跨租户实体须以
 * {@code tenantSearchable=false} 注册，避免在租户上下文被反向搜索而越权泄露。
 *
 * @param <T> 实体类型
 */
public final class LambdaNameSource<T> implements NameLookup, NameSearch {

    /** 反向搜索硬上限，防误传大 limit（下拉选择器最多回这么多条）。 */
    private static final int MAX_SEARCH_LIMIT = 20;

    private final String entityType;
    private final String displayLabel;
    private final boolean tenantSearchable;
    private final boolean referenceable;
    private final Class<T> entityClass;
    private final SFunction<T, ?> idGetter;
    private final List<DisplayField<T>> fields; // [0]=primary（必有），可选 [1]=secondary
    private final RenderMode render;

    LambdaNameSource(String entityType, String displayLabel, boolean tenantSearchable,
                     boolean referenceable, Class<T> entityClass, SFunction<T, ?> idGetter,
                     List<DisplayField<T>> fields, RenderMode render) {
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("名称源至少需声明 primary 字段：" + entityType);
        }
        this.entityType = entityType;
        this.displayLabel = displayLabel;
        this.tenantSearchable = tenantSearchable;
        this.referenceable = referenceable;
        this.entityClass = entityClass;
        this.idGetter = idGetter;
        this.fields = List.copyOf(fields);
        this.render = render;
    }

    private DisplayField<T> primary() {
        return fields.get(0);
    }

    private DisplayField<T> secondary() {
        return fields.size() > 1 ? fields.get(1) : null;
    }

    @Override
    public String entityType() {
        return entityType;
    }

    @Override
    public String displayLabel() {
        return displayLabel;
    }

    @Override
    public boolean tenantSearchable() {
        return tenantSearchable;
    }

    @Override
    public boolean referenceable() {
        return referenceable;
    }

    @Override
    public List<FieldMeta> searchFields() {
        List<FieldMeta> metas = new ArrayList<>(fields.size());
        for (DisplayField<T> f : fields) {
            metas.add(new FieldMeta(f.label(), f.match()));
        }
        return metas;
    }

    @Override
    public Map<Long, String> batchLookup(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<T> rows = projectedQuery().in(idGetter, ids).list();
        Map<Long, String> out = new HashMap<>(rows.size());
        for (T row : rows) {
            long id = ((Number) idGetter.apply(row)).longValue();
            String display = render.render(primary().getter().apply(row), secondaryValue(row));
            if (display != null) {
                out.put(id, display);
            }
        }
        return out;
    }

    @Override
    public List<NameHit> search(SearchQuery query) {
        if (query == null) {
            return List.of();
        }
        int limit = query.limit() <= 0 ? MAX_SEARCH_LIMIT : Math.min(query.limit(), MAX_SEARCH_LIMIT);
        int offset = Math.max(0, query.offset());
        String kw = trimToNull(query.keyword());
        if (kw == null) {
            return List.of();
        }
        Long idHint = parseId(kw); // 关键字纯数字时附带 id 精确命中
        // OR 组必须用 .and(...) 嵌套成括号：租户线拦截器会 append AND tenant_id，
        // 不加括号会因 AND 优先级高于 OR 而跨租户泄露（field=? OR ... AND tenant_id=?）。
        List<T> rows = projectedQuery()
                .and(g -> {
                    boolean[] added = {false};
                    for (DisplayField<T> f : fields) {
                        if (added[0]) g.or();
                        if (f.match() == MatchMode.PREFIX) {
                            g.likeRight(f.getter(), kw);
                        } else {
                            g.eq(f.getter(), kw);
                        }
                        added[0] = true;
                    }
                    if (idHint != null) {
                        if (added[0]) g.or();
                        g.eq(idGetter, idHint);
                    }
                })
                .orderByAsc(primary().getter())
                .last("limit " + limit + " offset " + offset)
                .list();

        List<NameHit> hits = new ArrayList<>(rows.size());
        for (T row : rows) {
            long id = ((Number) idGetter.apply(row)).longValue();
            String primaryVal = primary().getter().apply(row);
            String secondaryVal = secondaryValue(row);
            String display = render.render(primaryVal, secondaryVal);
            if (display == null) {
                display = String.valueOf(id);
            }
            hits.add(new NameHit(String.valueOf(id), display, primaryVal, secondaryVal));
        }
        return hits;
    }

    private String secondaryValue(T row) {
        DisplayField<T> sec = secondary();
        return sec == null ? null : sec.getter().apply(row);
    }

    /**
     * 只投影 id + 声明字段的查询。正 / 反向都只用这些列产出展示串，无需整行——投影列各自的 typeHandler
     * 仍生效（如加密列照常解密），只是不再拉取无关列。
     *
     * <p>必须在<b>单次</b> {@code select(...)} 里给全部列：MyBatis-Plus 的 {@code select} 是覆盖而非追加，
     * 分多次调只会留下最后一次、丢掉 id（命中行 id 为 null → NPE）。
     */
    @SuppressWarnings("unchecked")
    private LambdaQueryChainWrapper<T> projectedQuery() {
        SFunction<T, ?>[] cols = new SFunction[fields.size() + 1];
        cols[0] = idGetter;
        for (int i = 0; i < fields.size(); i++) {
            cols[i + 1] = fields.get(i).getter();
        }
        return Db.lambdaQuery(entityClass).select(cols);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** 纯数字（≤19 位）才解析为 id，否则返回 null。 */
    private static Long parseId(String s) {
        if (s == null || s.length() > 19) {
            return null;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return null;
            }
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
