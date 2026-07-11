package com.wensaas.core.api.namesource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 响应 DTO 字段级「ref 字段 → 经登记的转换源派生出目标值」回填声明（响应消费方侧）。
 *
 * <p>标在响应 DTO 的<b>派生字段</b>上，声明「该字段的值＝把兄弟 {@link #ref()} 字段经
 * {@link NameLookup#entityType()} 解析后的结果」。service 层构造完响应、调用一次
 * {@code TransFiller.fill(dto)} 即按本声明反射收集 ref、批量取数、原地回填——无需每个 ServiceImpl
 * 各写一套 {@code selectBatchIds → Map → 逐行 getOrDefault}。
 *
 * <p><b>★ 嵌套别忘 {@link TransDescend}</b>：{@code fill} 只穿透根对象本身；若本注解标在<b>嵌套</b>对象
 * （宿主对象的对象属性、或对象的集合 / Map / 数组元素）里，须在持有它的那个字段上标 {@link TransDescend}，
 * {@code fill} 才会下钻进来回填——漏标会<b>静默不回填（不报错）</b>。
 *
 * <p>转换不限于「id → 名」，{@code TransFiller} 按 {@link #type()} <b>分派</b>两类源：
 * <ul>
 *   <li><b>表基础引用</b>（{@link NameLookup} / {@code EntityNameResolver}）：{@code Long id → String 名}，
 *   形态固定、复用面广，底座内建实体的默认路径；</li>
 *   <li><b>自定义逻辑转换</b>（{@link ValueTransform}）：键、值<b>任意类型</b>（如 {@code code(String) → 名}、
 *   {@code code → 实体对象}、枚举→文案、复合拼装），业务注册 {@code ValueTransform} bean 即接入。</li>
 * </ul>
 * 同一 {@link #type()} 只能归一边（启动期校验冲突）。两路均按 {@link #type()} 分组批量解析、不写一行 SQL；
 * 翻不出（已删除 / 无值 / 未登记 type）一律保持目标字段原值（单值留 {@code null}、多值过滤该项），不抛，
 * 前端按原样保留的 ref 兄弟字段兜底。
 *
 * <p><b>目标字段类型约束（无启动期校验，按本约定写对）</b>：单值目标须为 {@code String} 可赋值类型；
 * 多值目标可为 {@code List} / {@code Collection} 可赋值类型、{@code Set} 可赋值类型、或<b>数组</b>——
 * {@code TransFiller} 按声明形态 materialize（{@code List}→{@code ArrayList}、{@code Set}→{@code LinkedHashSet}
 * 保序去重、数组→对应组件类型数组）。别声明成具体不兼容类型（如 {@code LinkedList} / {@code TreeSet} /
 * {@code HashSet}），materialize 的实现装不进。宿主 DTO 须为<b>可变 POJO</b>（Lombok {@code @Getter @Setter}）——
 * {@code TransFiller} 直接反射 {@code Field.set}，record / {@code final} 字段写不进。违反以上约定不在启动期拦
 * （免扫描开销），而在 {@code fill} 反射写回时抛 {@code IllegalStateException}（带字段名）。
 *
 * <p>支持两类目标字段：
 * <ul>
 *   <li><b>单值</b>：目标 {@code String}，{@link #ref()} 指向 {@code Long} 兄弟字段。
 *   <pre>{@code
 *   private Long mainDeptId;
 *   @TransField(type = EntityTypes.SYS_DEPT, ref = "mainDeptId")
 *   private String mainDeptName;
 *   }</pre></li>
 *   <li><b>多值</b>：目标为 {@code List<String>} / {@code Set<String>} / {@code String[]}，{@link #ref()} 指向
 *   {@code Collection<Long>} 或 {@code Long[]} / {@code long[]} 兄弟字段；逐 id 翻译、翻不出的项过滤掉
 *   （不混入 null）。同一 DTO 内多个同 {@link #type()} 且同 {@link #scope()} 的字段会聚合进同一次解析调用，省开销。
 *   <pre>{@code
 *   private List<Long> roleIds;
 *   @TransField(type = EntityTypes.SYS_ROLE, ref = "roleIds")
 *   private List<String> roleNames;
 *   }</pre></li>
 * </ul>
 *
 * <p><b>跨租户</b>：运营端翻别租户名时把 {@link #scope()} 设为 {@link TenantScope#CROSS}——{@code TransFiller}
 * 就地用 {@code IgnoreStrategyScopes} 旁路租户线，作用域只圈这一次解析、不放开主查询。{@code CROSS} 只允许
 * 出现在标了 {@link CrossTenantView} 的运营专属 DTO 上，否则启动期 / 解析期 fail-fast。
 *
 * <p>不覆盖 {@code @RelatedObjectGuarded} DTO（序列化期权限探针，关注点不同、时机不同；
 * 由 {@code TransSpecRegistry} 解析期 fail-fast 拦截两者同标一类）。
 *
 * <p><b>条件回填</b>：默认无条件覆盖目标字段。要按「目标 / 来源当前状态」有条件回填，用 {@link #skipIf()}
 * 声明若干 {@link FillSkip} 跳过模式，命中任一即跳过本次回填、保留目标原值。最常见的是
 * {@code skipIf = FillSkip.TARGET_NOT_BLANK}——目标已有内容就不覆盖。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TransField {

    /** 被引用实体 / 逻辑转换类型 code，取 {@link EntityTypes} 常量或业务自定义转换常量。 */
    String type();

    /**
     * 同一 DTO 内存放待转换值的兄弟字段名。单值目标对应 {@code Long} 字段，多值目标对应
     * {@code Collection<Long>} 字段；其值经 {@link #type()} 解析后写入本字段。
     */
    String ref();

    /**
     * 本字段解析时的租户作用域取向，默认 {@link TenantScope#FOLLOW}（跟随当前作用域，安全）。
     * 跨租户翻别租户名时设 {@link TenantScope#CROSS}（仅限标了 {@link CrossTenantView} 的运营专属 DTO）。
     */
    TenantScope scope() default TenantScope.FOLLOW;

    /**
     * 跳过模式（OR）：回填前逐项判定，命中任一即跳过本次回填、保留目标原值。默认空＝从不跳过
     * （始终回填，与历史行为一致）。各模式语义见 {@link FillSkip}。
     */
    FillSkip[] skipIf() default {};
}
