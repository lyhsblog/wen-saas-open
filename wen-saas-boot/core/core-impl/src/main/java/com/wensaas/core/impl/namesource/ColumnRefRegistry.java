package com.wensaas.core.impl.namesource;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.wensaas.core.api.namesource.DisplayRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * 实体字段引用注册表：{@code (表名, 列名) → 引用的实体类型}。
 *
 * <p>启动期扫描 {@code com.wensaas} 下所有 {@code @TableName} 实体，收集字段上的 {@link DisplayRef}
 * 声明，建成不可变索引。<b>消费方无关</b>的通用底座能力——首个消费方是数据变更日志渲染
 * （{@code AuditChangeRenderer}），后续可复用于「给 API 响应自动补 createdByName/ownerName 等关联翻译」。
 *
 * <p>列名解析：优先 {@code @TableField(value)}，否则按 MP 默认驼峰转下划线（与建表一致）。
 */
@Component
public class ColumnRefRegistry {

    private static final Logger log = LoggerFactory.getLogger(ColumnRefRegistry.class);
    private static final String SCAN_BASE = "com.wensaas";

    /** 单列引用声明。 */
    public record Ref(String entityType, String label) {
    }

    /** 表名 → (列名 → Ref)。 */
    private final Map<String, Map<String, Ref>> byTable;

    public ColumnRefRegistry() {
        this(scan());
    }

    /** 用预建索引构造（测试 / 程序化组合用）。 */
    public ColumnRefRegistry(Map<String, Map<String, Ref>> byTable) {
        this.byTable = byTable;
    }

    /** 取 (表,列) 的引用声明；未声明返回 {@code null}。 */
    public Ref refOf(String table, String column) {
        if (table == null || column == null) {
            return null;
        }
        Map<String, Ref> cols = byTable.get(table);
        return cols == null ? null : cols.get(column);
    }

    /** 一处列引用站点（表 + 列 + 引用的实体类型），供启动期 fail-fast 校验 entityType 合法性。 */
    public record RefSite(String table, String column, String entityType) {
    }

    /** 全部已登记的列引用站点（供 {@code DisplayRefValidator} 校验）。 */
    public java.util.List<RefSite> refSites() {
        java.util.List<RefSite> out = new java.util.ArrayList<>();
        byTable.forEach((table, cols) ->
                cols.forEach((col, ref) -> out.add(new RefSite(table, col, ref.entityType()))));
        return out;
    }

    private static Map<String, Map<String, Ref>> scan() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(TableName.class));

        Map<String, Map<String, Ref>> out = new HashMap<>();
        int count = 0;
        for (var bd : scanner.findCandidateComponents(SCAN_BASE)) {
            String className = bd.getBeanClassName();
            if (className == null) {
                continue;
            }
            Class<?> entity;
            try {
                entity = Class.forName(className);
            } catch (ClassNotFoundException e) {
                log.warn("columnref.scan.load.fail class={} reason={}", className, e.getMessage());
                continue;
            }
            TableName tn = entity.getAnnotation(TableName.class);
            if (tn == null || tn.value().isBlank()) {
                continue;
            }
            String table = tn.value();
            for (Field f : declaredFieldsWithSupers(entity)) {
                DisplayRef ref = f.getAnnotation(DisplayRef.class);
                if (ref == null) {
                    continue;
                }
                out.computeIfAbsent(table, k -> new HashMap<>())
                        .put(columnOf(f), new Ref(ref.value(), ref.label()));
                count++;
            }
        }
        log.info("columnref.scan.done tables={} refs={}", out.size(), count);
        return out;
    }

    private static String columnOf(Field f) {
        TableField tf = f.getAnnotation(TableField.class);
        if (tf != null && !tf.value().isBlank()) {
            return tf.value();
        }
        return StringUtils.camelToUnderline(f.getName());
    }

    private static Iterable<Field> declaredFieldsWithSupers(Class<?> type) {
        java.util.List<Field> fields = new java.util.ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                fields.add(f);
            }
        }
        return fields;
    }
}
