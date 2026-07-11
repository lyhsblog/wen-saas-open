package com.wensaas.core.impl.namesource;

import com.wensaas.core.api.namesource.CrossTenantView;
import com.wensaas.core.api.namesource.FillSkip;
import com.wensaas.core.api.namesource.TenantScope;
import com.wensaas.core.api.namesource.TransDescend;
import com.wensaas.core.api.namesource.TransField;
import com.wensaas.core.api.notification.access.RelatedObjectGuarded;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 按 DTO class 懒加载缓存 {@link TransField} 回填元数据。
 *
 * <p>首次遇到某 class 时反射解析其 {@code @TransField} 字段（连同 ref 取值字段、目标 setter、单/多值
 * 标志、{@link TenantScope}）与「需下钻的引用 / 集合字段」，缓存为 {@link ClassSpec} 后复用——后续请求
 * 直接取缓存，<b>不每请求全类反射扫注解</b>。缓存的是<b>类元数据</b>，非名称数据（名称仍每请求实时
 * resolve，不加跨请求缓存）。
 *
 * <p>剪枝：无 {@code @TransField} 且无可下钻字段的 class（叶子，如 {@code String} / {@code Long} /
 * {@code LocalDateTime}）记为 {@link ClassSpec#leaf()}，{@code TransFiller} 直接跳过、不空转反射。
 *
 * <p><b>解析期 fail-fast（泄露前即拦）</b>：
 * <ul>
 *   <li>{@code @TransField.ref} 指向不存在的字段 → 抛 {@link IllegalStateException}；</li>
 *   <li>字段用 {@link TenantScope#CROSS} 但宿主类未标 {@link CrossTenantView}（运营专属）→ 抛——
 *   拦「租户端 DTO 误标 CROSS → 静默翻出别租户数据」（非空泄露，弱告警兜不住）；</li>
 *   <li>宿主类同时标了 {@link RelatedObjectGuarded}（序列化期权限探针）与任一 {@code @TransField} → 抛——
 *   两者加工时机 / 关注点不同，禁止叠加（B3）。</li>
 * </ul>
 * 上述 CROSS / guarded 校验对任何被 {@code fill} 触达的 class 都生效（无盲区）；{@code TransFieldScanner}
 * 启动期再 force 解析所有 {@code @CrossTenantView} 类，把 opt-in 视图的问题提前到启动期。
 */
public class TransSpecRegistry {

    private final ConcurrentHashMap<Class<?>, ClassSpec> cache = new ConcurrentHashMap<>();

    /** 取（或首次解析并缓存）该 class 的回填元数据。 */
    public ClassSpec specFor(Class<?> type) {
        return cache.computeIfAbsent(type, TransSpecRegistry::parse);
    }

    private static ClassSpec parse(Class<?> type) {
        List<TransSpec> specs = new ArrayList<>();
        List<Field> childFields = new ArrayList<>();
        // @TransField 的目标字段、ref 字段都不参与下钻（它们是值标量或其集合，下钻无意义）。
        List<String> reserved = new ArrayList<>();

        // 先收集 @TransField 规格（含 ref 字段名，登记进 reserved）
        for (Field f : allFields(type)) {
            TransField ann = f.getAnnotation(TransField.class);
            if (ann == null) {
                continue;
            }
            Field refField = findField(type, ann.ref());
            if (refField == null) {
                throw new IllegalStateException("@TransField 的 ref 指向不存在的字段: " + type.getName()
                        + "#" + f.getName() + " -> ref=\"" + ann.ref() + "\"");
            }
            Class<?> ft = f.getType();
            TargetShape shape;
            Supplier<? extends Collection<Object>> collectionFactory = null;
            if (ft.isArray()) {
                shape = TargetShape.ARRAY;
            } else if (Collection.class.isAssignableFrom(ft)) {
                shape = TargetShape.COLLECTION;
                collectionFactory = pickCollectionFactory(ft);
                if (collectionFactory == null) {
                    throw new IllegalStateException("@TransField 多值目标的集合类型无受支持实现可赋值: " + type.getName()
                            + "#" + f.getName() + "(" + ft.getSimpleName() + ")，请声明 List / Set / Collection / SortedSet / Queue 等标准类型");
                }
            } else {
                shape = TargetShape.SINGLE;
            }
            if (shape != TargetShape.SINGLE) {
                Class<?> rt = refField.getType();
                if (!Collection.class.isAssignableFrom(rt) && !rt.isArray()) {
                    throw new IllegalStateException("@TransField 多值目标的 ref 字段须为集合或数组: " + type.getName()
                            + "#" + f.getName() + " -> ref=\"" + ann.ref() + "\"(" + rt.getSimpleName() + ")");
                }
            }
            boolean cross = ann.scope() == TenantScope.CROSS;
            if (cross && !type.isAnnotationPresent(CrossTenantView.class)) {
                throw new IllegalStateException("@TransField(scope = CROSS) 仅允许出现在标了 @CrossTenantView "
                        + "（运营专属）的 DTO 上，否则租户端误标会静默翻出别租户数据（泄露）: " + type.getName()
                        + "#" + f.getName());
            }
            f.setAccessible(true);
            refField.setAccessible(true);
            // 注解数组方法每次返回全新克隆，直接持有即可（内部消费，OR 语义下重复项无害）
            specs.add(new TransSpec(ann.type(), refField, f, shape, collectionFactory, cross, ann.skipIf()));
            reserved.add(f.getName());
            reserved.add(refField.getName());
        }

        // guarded 互斥：@RelatedObjectGuarded（序列化期权限探针）与 @TransField 不得同标一类（B3）
        if (!specs.isEmpty() && type.isAnnotationPresent(RelatedObjectGuarded.class)) {
            throw new IllegalStateException("@RelatedObjectGuarded 与 @TransField 不得叠加在同一 DTO 上"
                    + "（序列化期权限探针 vs 构造后回填，加工时机/关注点不同）: " + type.getName());
        }

        // 收集下钻字段：仅显式标了 @TransDescend 的（声明式，非自动；排除 @TransField 目标 / ref 字段）
        for (Field f : allFields(type)) {
            if (!f.isAnnotationPresent(TransDescend.class) || reserved.contains(f.getName())) {
                continue;
            }
            int mod = f.getModifiers();
            if (Modifier.isStatic(mod) || f.isSynthetic()) {
                continue;
            }
            if (!isDescendable(f.getType())) {
                throw new IllegalStateException("@TransDescend 只能标在可下钻字段（自有对象 / 集合 / Map / 数组）上: "
                        + type.getName() + "#" + f.getName() + "(" + f.getType().getSimpleName() + ")");
            }
            f.setAccessible(true);
            childFields.add(f);
        }
        return new ClassSpec(List.copyOf(specs), List.copyOf(childFields));
    }

    /**
     * 多值目标的候选集合实现，<b>按从宽到具体排序</b>：写回期对声明的字段类型逐个试
     * {@code 字段类型.isAssignableFrom(候选)}，取首个能赋值的。如此 {@code List}/{@code Collection} 落
     * {@code ArrayList}，{@code Set}/{@code HashSet}/{@code LinkedHashSet} 落 {@code LinkedHashSet}（保序去重），
     * {@code SortedSet}/{@code TreeSet} 落 {@code TreeSet}，{@code Queue}/{@code Deque} 落 {@code ArrayDeque}——
     * 用实际实现判定可赋值，比写死映射宽容（如 {@code HashSet} 字段也吃 {@code LinkedHashSet}）。
     */
    private static final List<Supplier<? extends Collection<Object>>> COLLECTION_IMPLS = List.of(
            ArrayList::new, LinkedHashSet::new, TreeSet::new, ArrayDeque::new);

    /** 选出可赋值给字段类型的集合工厂；无可用实现返回 {@code null}（调用方 fail-fast）。 */
    private static Supplier<? extends Collection<Object>> pickCollectionFactory(Class<?> fieldType) {
        for (Supplier<? extends Collection<Object>> factory : COLLECTION_IMPLS) {
            if (fieldType.isAssignableFrom(factory.get().getClass())) {
                return factory;
            }
        }
        return null;
    }

    /** 校验 @TransDescend 标注的字段类型确实可下钻（集合 / Map / 数组 / 自有 POJO；非标量 / JDK 值类型）。 */
    private static boolean isDescendable(Class<?> t) {
        if (Collection.class.isAssignableFrom(t) || Map.class.isAssignableFrom(t) || t.isArray()) {
            return true;
        }
        if (t.isPrimitive() || t.isEnum()) {
            return false;
        }
        String n = t.getName();
        // JDK / 框架自带值类型视为叶子；自有 POJO（com.wensaas.* 等）需下钻
        return !(n.startsWith("java.") || n.startsWith("jdk.") || n.startsWith("javax.") || n.startsWith("jakarta."));
    }

    private static List<Field> allFields(Class<?> type) {
        List<Field> out = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            out.addAll(List.of(c.getDeclaredFields()));
        }
        return out;
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // 继续往父类找
            }
        }
        return null;
    }

    /** 目标字段容器形态：单值 / 集合（List/Set/Queue…，由 {@code collectionFactory} 定实现）/ 数组。 */
    public enum TargetShape {
        SINGLE, COLLECTION, ARRAY
    }

    /**
     * 单条 {@code @TransField} 回填规格。{@code shape} 为目标容器形态；{@code collectionFactory} 为
     * {@code COLLECTION} 时按字段声明类型选出的可赋值集合工厂（其余形态为 {@code null}）；{@code cross} 为
     * {@code true} 时该字段解析需旁路租户线；{@code skipIf} 为命中即跳过本次回填的模式集（空＝从不跳过）。
     */
    public record TransSpec(String type, Field refField, Field targetField, TargetShape shape,
                            Supplier<? extends Collection<Object>> collectionFactory, boolean cross,
                            FillSkip[] skipIf) {

        /** 是否多值目标（集合 / 数组）。 */
        public boolean multi() {
            return shape != TargetShape.SINGLE;
        }
    }

    /** 一个 class 的回填元数据：{@code @TransField} 规格 + 需下钻字段。 */
    public record ClassSpec(List<TransSpec> transSpecs, List<Field> childFields) {
        /** 无回填字段且无可下钻字段 → 叶子，遍历时整体跳过。 */
        public boolean leaf() {
            return transSpecs.isEmpty() && childFields.isEmpty();
        }
    }
}
