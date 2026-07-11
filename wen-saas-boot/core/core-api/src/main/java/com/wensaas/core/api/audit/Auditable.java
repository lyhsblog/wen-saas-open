package com.wensaas.core.api.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记业务方法需要写入操作日志（{@code sys_operation_log}）。
 *
 * <p>由 {@code AuditableAspect} 拦截：记录一条 ② 操作日志（谁/动作/对象/HTTP 信封），
 * 异常路径仍记录一条，{@code error_message} = 异常消息；{@code status_code} 由
 * {@code PendingAuditFlushFilter} 在响应提交后读取真实 HTTP 状态回填（如 422/409）。
 * <b>字段级"改前/改后"不再由本注解捕获</b>，统一由 ③ 数据变更日志（PG 触发器）记录，
 * 按 {@code requestId} 关联。
 *
 * <p>SpEL 表达式可访问：方法参数名（如 {@code #request}）、返回值（{@code #result}）。
 * 项目根 pom 已开启 {@code <parameters>true</parameters>}，可直接用参数名。
 *
 * <pre>{@code
 * @Auditable(module = "crm", action = "新建客户", objectType = EntityTypes.SYS_TENANT,
 *            objectIdExpr = "#result.id", objectDescExpr = "#result.name")
 * public Customer createCustomer(CustomerCreateRequest request) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {

    /** 业务模块编码（对应 {@code BusinessModuleDescriptor.metadata.code}）。底座操作填 {@code "__core__"} 或留空。 */
    String module() default "";

    /**
     * 动作（操作类型）：<b>直接写中文</b>，作为操作日志「操作类型」列原样展示，前端不再翻译。
     *
     * <p>action 是自由字符串、无后端枚举；写中文既省翻译层，业务新增动作也不必动底座。
     * 注意 {@link #objectType()} 与 action 不同惯例：objectType 须引用实体 code（见其文档）。
     *
     * <p><b>「动词+对象」自描述</b>：光看「操作类型」列即知动作，不依赖对象类型列反推（objectType 已 code 化、
     * 部分操作无对应实体而留空）。CRUD 写 {@code 新建角色 / 编辑部门 / 删除套餐 / 切换成员状态 / 停用通知模板 / 提交工单}，
     * <b>不写裸「新建/更新/删除」</b>。其余按业务命名中文短语（如「认领」「转交」「代登录」）。
     *
     * <p>颜色约定（前端按关键词上色，措辞须保留命中词）：含「新建/启用/激活/分配」→绿，
     * 「删除/停用/重置/解绑/移除」→红，「代登录/暂停/强制」→橙，「更新/修改/编辑/切换/调整/权限」→蓝。
     */
    String action();

    /**
     * 对象类型：须填**已注册实体 code**（{@code EntityTypes.SYS_*}，如 {@code EntityTypes.SYS_USER}），
     * 或留空。读取侧据此经 {@code EntityNameResolver.displayLabel} 派生中文「对象类型」列；
     * 对象友好名走 {@code objectDescExpr} / {@code AuditObjects.add}。无对应实体的操作留空、语义由 action 承载。
     * <p>启动期由 {@code AuditObjectTypeValidator} 校验（非 code/非空即 fail-fast），防中文魔法值漂移。
     */
    String objectType() default "";

    /** SpEL；为空时 {@code object_id} 留 null。 */
    String objectIdExpr() default "";

    /** SpEL；为空时 {@code object_desc} 留 null。 */
    String objectDescExpr() default "";

    /**
     * 是否记录读操作。默认 {@code false}（普通读操作不记录，仅写操作记录）；
     * 显式声明的"敏感读"（如"导出客户列表"）才设 {@code true}。
     */
    boolean recordRead() default false;

    /**
     * SpEL；求值为 Long，注入 {@code AuditEvent.targetTenantId}。
     * 用于运营对租户操作时记录目标租户（双向审计）。为空则 {@code targetTenantId} 留 null。
     * 典型用法：{@code "#tenantId"} 或
     * {@code "T(com.wensaas.core.api.auth.user.UserContext).require().tenantId()"}。
     */
    String targetTenantIdExpr() default "";
}
