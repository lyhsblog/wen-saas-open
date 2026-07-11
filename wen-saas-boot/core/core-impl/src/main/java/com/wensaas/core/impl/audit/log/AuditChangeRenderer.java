package com.wensaas.core.impl.audit.log;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.wensaas.core.api.ops.audit.OpsOperationLogQueryService.DataChange;
import com.wensaas.core.api.ops.audit.OpsOperationLogQueryService.FieldChange;
import com.wensaas.core.impl.namesource.ColumnRefRegistry;
import com.wensaas.core.impl.namesource.EntityNameResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ③ 数据变更日志「成句」渲染器。
 *
 * <p>把 {@code sys_data_change_log.changes}（原始 {@code {列:{before,after}}}）按
 * {@code sys_audit_table_config} 的 {@code label}/{@code render} 译成人话：
 * <ul>
 *   <li>{@code SCALAR}：原值文本</li>
 *   <li>{@code ENUM:<dict>}：查内置枚举字典（{@link #ENUM_DICTS}）译，如 status {@code 1→启用}</li>
 *   <li>{@code REF:<entityType>}：经 {@link EntityNameResolver} 按 id 批量翻成 {@code 名称(id)}
 *   （best-effort，引用行删除/无名则回退原 id）。展示列由 entityType 的 NameSource 登记，审计配置不再重复表.列</li>
 *   <li>{@code MASK}：触发器已写 {@code "<已变更>"} 占位，原样透出</li>
 * </ul>
 *
 * <p>列字典是单一真相源：未配置的列回退列名 + 原值。REF 走类型化的 NameSource 注册（无裸 SQL、无注入面），
 * 整页一次性批量解析（消除逐格 N+1）。
 */
public class AuditChangeRenderer {

    private static final Logger log = LoggerFactory.getLogger(AuditChangeRenderer.class);

    /** 内置枚举字典：dictCode → (原值文本 → 中文)。取值见对应建表注释，平台级稳定枚举。 */
    private static final Map<String, Map<String, String>> ENUM_DICTS = Map.of(
            "commonStatus", Map.of("1", "启用", "0", "停用"),
            "tenantStatus", Map.of("0", "暂停", "1", "正常", "2", "已过期"),
            "packageStatus", Map.of("0", "草稿", "1", "上架", "2", "归档", "3", "下架"),
            "dataScope", Map.of("1", "全部数据", "2", "本部门+下属", "3", "仅本人创建"),
            "gender", Map.of("0", "未知", "1", "男", "2", "女")
    );

    private static final String REF_PREFIX = "REF:";
    private static final String ENUM_PREFIX = "ENUM:";

    private final SysAuditTableConfigMapper configMapper;
    private final EntityNameResolver entityNameResolver;
    private final ColumnRefRegistry columnRefRegistry;
    private final ObjectMapper jackson;

    public AuditChangeRenderer(SysAuditTableConfigMapper configMapper, EntityNameResolver entityNameResolver,
                               ColumnRefRegistry columnRefRegistry, ObjectMapper jackson) {
        this.configMapper = configMapper;
        this.entityNameResolver = entityNameResolver;
        this.columnRefRegistry = columnRefRegistry;
        this.jackson = jackson;
    }

    /** 把 ③ 行渲染成结构化、已成句的 {@link DataChange} 列表。 */
    public List<DataChange> render(List<SysDataChangeLog> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<String, Map<String, SysAuditTableConfig>> cfg = loadConfig();

        // 1) 整页 changes JSON 各解析一次（renderFields 复用，不二次解析）
        List<JsonNode> trees = new ArrayList<>(rows.size());
        for (SysDataChangeLog row : rows) {
            trees.add(parse(row.getChanges()));
        }

        // 2) 聚合每个 entityType 下所有 REF id，单次批量翻名（消除逐格 N+1）
        Map<String, Set<Long>> refIdsByType = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            JsonNode tree = trees.get(i);
            if (tree == null) {
                continue;
            }
            String table = rows.get(i).getTableName();
            Map<String, SysAuditTableConfig> cols = cfg.getOrDefault(table, Map.of());
            tree.properties().forEach(e -> {
                String render = effectiveRender(table, e.getKey(), cols.get(e.getKey()));
                if (render.startsWith(REF_PREFIX)) {
                    String type = render.substring(REF_PREFIX.length());
                    collectId(refIdsByType, type, e.getValue().get("before"));
                    collectId(refIdsByType, type, e.getValue().get("after"));
                }
            });
        }
        Map<String, Map<Long, String>> namesByType = new HashMap<>();
        for (Map.Entry<String, Set<Long>> e : refIdsByType.entrySet()) {
            namesByType.put(e.getKey(), entityNameResolver.resolve(e.getKey(), e.getValue()));
        }

        // 3) 逐行渲染
        List<DataChange> out = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            SysDataChangeLog row = rows.get(i);
            Map<String, SysAuditTableConfig> cols = cfg.getOrDefault(row.getTableName(), Map.of());
            out.add(new DataChange(row.getTableName(), row.getOp(), row.getRowPk(),
                    renderFields(row.getTableName(), trees.get(i), cols, namesByType)));
        }
        return out;
    }

    private JsonNode parse(String changesJson) {
        if (changesJson == null || changesJson.isBlank()) {
            return null;
        }
        try {
            return jackson.readTree(changesJson);
        } catch (Exception e) {
            log.warn("audit.render.parse.fail reason={}", e.getMessage());
            return null;
        }
    }

    private List<FieldChange> renderFields(String table, JsonNode tree, Map<String, SysAuditTableConfig> cols,
                                           Map<String, Map<Long, String>> namesByType) {
        List<FieldChange> fields = new ArrayList<>();
        if (tree == null) {
            return fields;
        }
        tree.properties().forEach(entry -> {
            String column = entry.getKey();
            JsonNode bv = entry.getValue().get("before");
            JsonNode av = entry.getValue().get("after");
            SysAuditTableConfig c = cols.get(column);
            String render = effectiveRender(table, column, c);
            fields.add(new FieldChange(column, effectiveLabel(table, column, c),
                    renderValue(render, bv, namesByType), renderValue(render, av, namesByType)));
        });
        return fields;
    }

    /** 渲染策略：审计配置显式声明优先，否则取 {@link ColumnRefRegistry} 的 REF 声明，再否则 SCALAR。 */
    private String effectiveRender(String table, String column, SysAuditTableConfig c) {
        if (c != null && c.getRender() != null) {
            return c.getRender();
        }
        ColumnRefRegistry.Ref ref = columnRefRegistry.refOf(table, column);
        return ref != null ? REF_PREFIX + ref.entityType() : "SCALAR";
    }

    /** 列标签：审计配置优先，否则取 {@code @DisplayRef(label)}，再否则回退列名。 */
    private String effectiveLabel(String table, String column, SysAuditTableConfig c) {
        if (c != null && c.getLabel() != null) {
            return c.getLabel();
        }
        ColumnRefRegistry.Ref ref = columnRefRegistry.refOf(table, column);
        if (ref != null && !ref.label().isBlank()) {
            return ref.label();
        }
        return column;
    }

    /** 单值渲染。{@code node} 为 changes JSON 里的 before/after 节点。 */
    private String renderValue(String render, JsonNode node, Map<String, Map<Long, String>> namesByType) {
        if (node == null || node.isNull()) {
            return null;
        }
        String raw = node.isValueNode() ? node.asString() : node.toString();
        if (render == null || "SCALAR".equals(render) || "MASK".equals(render)) {
            return raw;
        }
        if (render.startsWith(ENUM_PREFIX)) {
            Map<String, String> dict = ENUM_DICTS.get(render.substring(ENUM_PREFIX.length()));
            return dict != null ? dict.getOrDefault(raw, raw) : raw;
        }
        if (render.startsWith(REF_PREFIX)) {
            String type = render.substring(REF_PREFIX.length());
            Long id = asId(node);
            String name = id == null ? null : namesByType.getOrDefault(type, Map.of()).get(id);
            return name == null ? raw : name + "(" + raw + ")";
        }
        return raw;
    }

    private static void collectId(Map<String, Set<Long>> acc, String type, JsonNode node) {
        Long id = asId(node);
        if (id != null) {
            acc.computeIfAbsent(type, k -> new HashSet<>()).add(id);
        }
    }

    /** 取标量节点的 Long id；非数值 / 非标量返回 null。 */
    private static Long asId(JsonNode node) {
        if (node == null || node.isNull() || !node.isValueNode()) {
            return null;
        }
        try {
            return Long.valueOf(node.asString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, Map<String, SysAuditTableConfig>> loadConfig() {
        Map<String, Map<String, SysAuditTableConfig>> out = new HashMap<>();
        for (SysAuditTableConfig c : configMapper.selectList(null)) {
            if ("*".equals(c.getColumnName())) {
                continue; // 表级登记行，渲染时忽略
            }
            out.computeIfAbsent(c.getTableName(), k -> new HashMap<>()).put(c.getColumnName(), c);
        }
        return out;
    }
}
