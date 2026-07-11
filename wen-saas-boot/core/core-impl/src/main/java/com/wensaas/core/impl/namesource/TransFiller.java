package com.wensaas.core.impl.namesource;

import com.wensaas.core.api.namesource.FillSkip;
import com.wensaas.core.api.namesource.TransFill;
import com.wensaas.core.api.namesource.ValueTransform;
import com.wensaas.core.impl.namesource.TransSpecRegistry.ClassSpec;
import com.wensaas.core.impl.namesource.TransSpecRegistry.TargetShape;
import com.wensaas.core.impl.namesource.TransSpecRegistry.TransSpec;
import com.wensaas.core.impl.tenant.isolation.IgnoreStrategyScopes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 响应 DTO 的「ref 字段 → 经登记转换源派生目标值」批量反射回填内核（service 层、构造响应后调用）。
 *
 * <p>单例、无状态。一行 {@code fill(dto)} 完成「反射收集 ref → 批量取数 → 原地 set」（按 type 分组、一次取数）。
 * 取数<b>按 type 分派</b>：命中已注册的 {@link com.wensaas.core.api.namesource.ValueTransform}（自定义逻辑转换，
 * 键值任意类型）走它；否则回落 {@link EntityNameResolver}（表基础引用，{@code Long → String}）。本类不写
 * 一行 SQL，键<b>原样</b>收集、不强转。
 *
 * <p><b>两遍遍历，批量语义在递归下不变</b>：
 * <ol>
 *   <li>第一遍走对象图：根对象（及作为根直接传入的 {@code List} / {@code Map} / 数组）必穿透；宿主对象的
 *   嵌套对象属性<b>仅当字段标了 {@link com.wensaas.core.api.namesource.TransDescend} 才下钻</b>（声明式，
 *   不自动铺开），{@link IdentityHashMap} 防环，把所有 {@code (type, scope, key)} 收集去重；</li>
 *   <li>按 {@code (type, scope)} 批量 {@code resolve}（<b>N 个 (type,scope) → N 条 SQL，与行数 / 嵌套
 *   深度无关</b>）；</li>
 *   <li>反射 set 回各目标字段。</li>
 * </ol>
 * 同一 {@code (type, scope)} 的多个字段（单值 / 多值、同对象 / 跨对象）自动聚合进同一次 {@code resolve}。
 *
 * <p><b>跨租户作用域就地承载</b>：{@link com.wensaas.core.api.namesource.TenantScope#CROSS} 的组，
 * 在 {@code resolve} 外包一层 {@code IgnoreStrategyScopes.enter(TENANT_LINE)} 旁路租户线——作用域只圈
 * 这一次名称解析、不放开主查询，且不经 AOP（不需要给源单独写非-final {@code @Component} 挂注解）。
 * {@code CROSS} 的合法性（仅限 {@code @CrossTenantView} DTO）由 {@link TransSpecRegistry} 解析期拦截。
 *
 * <p>翻不出（已删除 / 无值 / 未登记 type / {@code render} 产 null）一律保持单值目标 {@code null}、
 * 多值目标过滤掉该项（不混入 null），<b>不抛</b>；前端按 ref 兄弟字段兜底。
 */
public class TransFiller implements TransFill {

    private static final Logger log = LoggerFactory.getLogger(TransFiller.class);

    private final EntityNameResolver resolver;
    private final TransSpecRegistry registry;
    private final ValueTransformRegistry transforms;

    public TransFiller(EntityNameResolver resolver, TransSpecRegistry registry, ValueTransformRegistry transforms) {
        this.resolver = resolver;
        this.registry = registry;
        this.transforms = transforms;
    }

    /** 回填单个对象（及其对象图）；根也可直接是集合 / Map / 数组。业务模块经 {@link TransFill} 接口注入调用。 */
    @Override
    public void fill(Object dto) {
        if (dto == null) {
            return;
        }
        Map<Object, Boolean> visited = new IdentityHashMap<>();
        Map<WantKey, Set<Object>> wanted = new HashMap<>();
        List<FillTask> tasks = new ArrayList<>();
        collect(dto, visited, wanted, tasks);
        if (tasks.isEmpty()) {
            return;
        }
        Map<WantKey, Map<Object, Object>> resolved = new HashMap<>(wanted.size());
        for (Map.Entry<WantKey, Set<Object>> e : wanted.entrySet()) {
            WantKey key = e.getKey();
            Map<Object, Object> values = resolveGroup(key, e.getValue());
            resolved.put(key, values);
            warnIfPlatformOnlyMiss(key, e.getValue(), values);
        }
        for (FillTask t : tasks) {
            writeBack(t, resolved);
        }
    }

    /** 回填一批对象（等价于把整个集合当作根对象图遍历）。 */
    public void fill(Collection<?> dtos) {
        fill((Object) dtos);
    }

    /**
     * 解析一组 {@code (type, scope)} 的键→值：先查自定义转换 {@link ValueTransform}（键值任意类型），
     * 命中即走它；否则回落表基础引用（{@code Long → String}）。{@code scope = CROSS} 时整段包一层旁路租户线，
     * 对两条路径一致生效。
     */
    private Map<Object, Object> resolveGroup(WantKey key, Set<Object> keys) {
        if (!key.cross()) {
            return resolveDispatch(key.type(), keys);
        }
        try (var ignored = IgnoreStrategyScopes.enter(IgnoreStrategyScopes.TENANT_LINE)) {
            return resolveDispatch(key.type(), keys);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Object, Object> resolveDispatch(String type, Set<Object> keys) {
        ValueTransform transform = transforms == null ? null : transforms.find(type);
        if (transform != null) {
            Map<Object, Object> out = transform.transform(keys);
            return out == null ? Map.of() : out;
        }
        // 表引用：键须为 Long（ref 字段本就是 Long/Long 集合/long[]），结果 Map<Long,String> 上转为 Map<Object,Object>
        return (Map<Object, Object>) (Map<?, ?>) resolver.resolve(type, toLongs(keys));
    }

    // ===================== 第一遍：遍历收集 =====================

    private void collect(Object node, Map<Object, Boolean> visited,
                         Map<WantKey, Set<Object>> wanted, List<FillTask> tasks) {
        if (node == null) {
            return;
        }
        if (node instanceof Collection<?> col) {
            for (Object e : col) {
                collect(e, visited, wanted, tasks);
            }
            return;
        }
        if (node instanceof Map<?, ?> map) {
            for (Object v : map.values()) {
                collect(v, visited, wanted, tasks);
            }
            return;
        }
        Class<?> c = node.getClass();
        if (c.isArray()) {
            if (!c.getComponentType().isPrimitive()) {
                for (Object e : (Object[]) node) {
                    collect(e, visited, wanted, tasks);
                }
            }
            return;
        }
        if (isLeaf(c)) {
            return;
        }
        if (visited.put(node, Boolean.TRUE) != null) {
            return; // 防环：同一实例已访问
        }
        ClassSpec spec = registry.specFor(c);
        if (spec.leaf()) {
            return;
        }
        for (TransSpec ts : spec.transSpecs()) {
            collectField(node, ts, wanted, tasks);
        }
        for (Field child : spec.childFields()) {
            collect(read(child, node), visited, wanted, tasks);
        }
    }

    /**
     * 建任务（含跳过门控）：命中 {@code skipIf} 即整字段不建任务、不登记键（写回期不再触达该字段）；
     * 否则把待翻键登进 {@code wanted} 批量取数，并生成一条 {@link FillTask} 供写回期直接 set。键<b>原样</b>
     * 收集（不强转 Long）——表引用键本就是 Long，自定义转换键可为任意类型。
     */
    private void collectField(Object node, TransSpec ts, Map<WantKey, Set<Object>> wanted, List<FillTask> tasks) {
        Object refVal = read(ts.refField(), node);
        if (shouldSkip(ts.skipIf(), refVal, read(ts.targetField(), node))) {
            return; // 命中跳过模式：保留目标原值
        }
        WantKey key = new WantKey(ts.type(), ts.cross());
        if (ts.multi()) {
            if (refVal instanceof Collection<?> || isArray(refVal)) { // 非集合 / 非数组（含 null）→ 不建任务，保持原值
                List<Object> keys = toKeys(refVal);
                wanted.computeIfAbsent(key, k -> new HashSet<>()).addAll(keys);
                tasks.add(new FillTask(node, ts.targetField(), ts.shape(), ts.collectionFactory(), key, null, keys));
            }
        } else if (refVal != null) { // 单值键 null → 不建任务，保持原值
            wanted.computeIfAbsent(key, k -> new HashSet<>()).add(refVal);
            tasks.add(new FillTask(node, ts.targetField(), TargetShape.SINGLE, null, key, refVal, null));
        }
    }

    // ===================== 第二遍：按任务列表反射回填 =====================

    private void writeBack(FillTask t, Map<WantKey, Map<Object, Object>> resolved) {
        Map<Object, Object> values = resolved.get(t.key());
        if (t.shape() == TargetShape.SINGLE) {
            Object value = values == null ? null : values.get(t.key0());
            if (value != null) {
                write(t.target(), t.node(), value); // 翻不出 → 保持原值(null)
            }
            return;
        }
        List<Object> out = new ArrayList<>(t.keys().size());
        for (Object k : t.keys()) {
            Object value = values == null ? null : values.get(k);
            if (value != null) {
                out.add(value); // 翻不出的项过滤掉，不混入 null
            }
        }
        write(t.target(), t.node(), materialize(t, out));
    }

    /** 按目标容器形态把翻好的值 materialize：数组→组件类型数组；集合→解析期选定工厂的实例（addAll）。 */
    private static Object materialize(FillTask t, List<Object> values) {
        if (t.shape() == TargetShape.ARRAY) {
            Class<?> component = t.target().getType().getComponentType();
            Object arr = Array.newInstance(component, values.size());
            for (int i = 0; i < values.size(); i++) {
                Array.set(arr, i, values.get(i));
            }
            return arr;
        }
        Collection<Object> c = t.collectionFactory().get(); // COLLECTION：Set 自然去重、保序由实现决定
        c.addAll(values);
        return c;
    }

    /**
     * 弱告警护栏：platform-only / 跨租户语义的 type 在响应回填中请求了 id 却拿到
     * <b>空</b>结果时，{@code log.warn} 疑似该字段漏设 {@code scope = CROSS}（或 id 确不存在）。
     * <b>只告警、不抛、不读 {@code IgnoreStrategyScopes} 内部栈</b>。注意它只覆盖「漏设 → 查空」，覆盖不了
     * 「租户端误用 {@code CROSS} → 查出别租户数据（非空泄露）」——后者归 {@link TransSpecRegistry} 解析期 +
     * {@code TransFieldScanner} 启动期 fail-fast。
     */
    private void warnIfPlatformOnlyMiss(WantKey key, Set<Object> keys, Map<Object, Object> values) {
        if (!key.cross() && !keys.isEmpty() && (values == null || values.isEmpty())
                && resolver.isPlatformOnly(key.type())) {
            log.warn("transfill.platformonly.miss type={} ids={} —— 疑似该字段漏设 scope=CROSS"
                    + "（platform-only/跨租户语义源带租户线查空，名留 null）；若 id 确不存在可忽略",
                    key.type(), keys.size());
        }
    }

    // ===================== 工具 =====================

    /** 批量取数去重键：同 type 同 scope 才合并进一次 resolve（CROSS 旁路租户线、FOLLOW 不旁路，不可混批）。 */
    private record WantKey(String type, boolean cross) {
    }

    /**
     * 一条「待回填字段」任务（收集期建、写回期消费）：跳过的字段天然不在列。{@code SINGLE} 用 {@code key0}
     * （{@code keys=null}）；多值（COLLECTION / ARRAY）用有序 {@code keys}（{@code key0=null}），写回按 {@code shape}
     * + {@code collectionFactory}（仅 COLLECTION）materialize 成对应容器。键值均为任意类型（表引用为 Long/String）。
     */
    private record FillTask(Object node, Field target, TargetShape shape,
                           Supplier<? extends Collection<Object>> collectionFactory,
                           WantKey key, Object key0, List<Object> keys) {
    }

    /** 跳过判定（OR）：命中任一模式即跳过本次回填。空数组（默认）→ 循环不执行 → 恒不跳过（历史无条件回填）。 */
    private static boolean shouldSkip(FillSkip[] modes, Object source, Object target) {
        for (FillSkip m : modes) {
            boolean hit = switch (m) {
                case TARGET_NOT_NULL -> target != null;
                case TARGET_NOT_BLANK -> !isBlank(target);
                case SOURCE_NULL -> source == null;
                case SOURCE_BLANK -> isBlank(source);
                case SOURCE_ZERO -> source instanceof Number n && n.longValue() == 0L;
            };
            if (hit) {
                return true;
            }
        }
        return false;
    }

    /** 空口径：标量 null / 集合 / Map / 数组为空 / 空白字符串。非空标量（如 Long）→ 非空。 */
    private static boolean isBlank(Object v) {
        if (v == null) {
            return true;
        }
        if (v instanceof Collection<?> c) {
            return c.isEmpty();
        }
        if (v instanceof Map<?, ?> m) {
            return m.isEmpty();
        }
        if (v instanceof CharSequence cs) {
            return cs.toString().isBlank();
        }
        if (v.getClass().isArray()) {
            return Array.getLength(v) == 0;
        }
        return false;
    }

    /** 集合 / Map / 数组已在 collect 前置分支处理；此处只需判标量叶子。 */
    private static boolean isLeaf(Class<?> c) {
        if (c.isEnum()) {
            return true;
        }
        String n = c.getName();
        return n.startsWith("java.") || n.startsWith("jdk.")
                || n.startsWith("javax.") || n.startsWith("jakarta.");
    }

    private static Long toLong(Object o) {
        return o instanceof Number n ? n.longValue() : null;
    }

    private static boolean isArray(Object o) {
        return o != null && o.getClass().isArray();
    }

    /** 从集合或数组型 ref 抽出有序、去 null 的<b>原始</b>键列表（不强转；数组含 {@code long[]} 等，逐元素装箱）。 */
    private static List<Object> toKeys(Object refVal) {
        List<Object> keys = new ArrayList<>();
        if (refVal instanceof Collection<?> col) {
            for (Object o : col) {
                if (o != null) {
                    keys.add(o);
                }
            }
        } else if (isArray(refVal)) {
            int n = Array.getLength(refVal);
            for (int i = 0; i < n; i++) {
                Object o = Array.get(refVal, i); // 基本类型数组在此装箱
                if (o != null) {
                    keys.add(o);
                }
            }
        }
        return keys;
    }

    /** 表引用分派用：键转 {@code Long}（ref 键本就是数值），非数值丢弃。 */
    private static List<Long> toLongs(Collection<Object> keys) {
        List<Long> out = new ArrayList<>(keys.size());
        for (Object k : keys) {
            Long id = toLong(k);
            if (id != null) {
                out.add(id);
            }
        }
        return out;
    }

    private static Object read(Field f, Object target) {
        try {
            return f.get(target);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            throw new IllegalStateException("TransFiller 读取字段失败: " + f, e);
        }
    }

    // IllegalArgumentException：目标字段类型装不下回填值（如多值目标非 List/Collection），一并包成带字段名的诊断
    private static void write(Field f, Object target, Object value) {
        try {
            f.set(target, value);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            throw new IllegalStateException("TransFiller 写入字段失败: " + f, e);
        }
    }
}
