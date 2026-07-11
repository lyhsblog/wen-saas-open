package com.wensaas.core.impl.tenant.isolation;

import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * MP {@link InterceptorIgnoreHelper} 的<b>可嵌套</b>作用域封装。
 *
 * <p>背景（2026-06-10，JourneyOne 偶发失败定位）：MP 3.5.x 的 {@code InterceptorIgnoreHelper}
 * 是普通 ThreadLocal——{@code handle} 直接覆盖、{@code clearIgnoreStrategy} 整体清空、<b>无栈语义</b>。
 * 嵌套使用时内层 clear 会把外层策略一并清掉：典型链路
 * {@code @CrossTenantQuery 服务方法 → permissionResolver 解析权限 → 内部 handle/clear →
 * 外层租户旁路失效 → 后续混合表查询被注入 tenant_id 条件 → 查不到数据}。
 *
 * <p>本工具自管每线程的作用域栈：进入时把<b>栈内全部策略的并集</b>写给 MP，退出时弹栈并
 * 恢复剩余并集（栈空才真正 clear）。嵌套任意深度、跨 tenantLine / dataPermission 混合均安全。
 *
 * <p>用法（推荐 try-with-resources）：
 * <pre>{@code
 * try (var ignored = IgnoreStrategyScopes.enter(IgnoreStrategyScopes.TENANT_LINE)) {
 *     // 期间本线程 SQL 跳过 TenantLine 改写
 * }
 * }</pre>
 *
 * <p>注意：与裸 {@code InterceptorIgnoreHelper.handle/clear} 混用时，裸 clear 仍会破坏本工具
 * 已生效的策略——增量迁移期间，凡可能被 {@code @CrossTenantQuery} 方法间接调用的代码必须走本工具。
 */
public final class IgnoreStrategyScopes {

    /** 跳过租户行拦截（TenantLineInnerInterceptor）。 */
    public static final IgnoreStrategy TENANT_LINE = IgnoreStrategy.builder().tenantLine(true).build();
    /** 跳过数据权限拦截（DataPermissionInterceptor）。 */
    public static final IgnoreStrategy DATA_PERMISSION = IgnoreStrategy.builder().dataPermission(true).build();

    private static final ThreadLocal<Deque<IgnoreStrategy>> STACK = new ThreadLocal<>();

    private IgnoreStrategyScopes() {
    }

    /** 进入一个忽略作用域；返回句柄用于 try-with-resources 自动退出。 */
    public static Scope enter(IgnoreStrategy strategy) {
        Deque<IgnoreStrategy> stack = STACK.get();
        if (stack == null) {
            stack = new ArrayDeque<>(4);
            STACK.set(stack);
        }
        stack.push(strategy);
        InterceptorIgnoreHelper.clearIgnoreStrategy();
        InterceptorIgnoreHelper.handle(merged(stack));
        return Scope.INSTANCE;
    }

    /** 退出最近一层作用域，恢复外层策略并集；栈空则彻底清理。 */
    public static void exit() {
        Deque<IgnoreStrategy> stack = STACK.get();
        InterceptorIgnoreHelper.clearIgnoreStrategy();
        if (stack == null || stack.isEmpty()) {
            STACK.remove();
            return;
        }
        stack.pop();
        if (stack.isEmpty()) {
            STACK.remove();
        } else {
            InterceptorIgnoreHelper.handle(merged(stack));
        }
    }

    /** 栈内全部策略的并集（Boolean 字段 null 安全 OR；others 按 key 合并，true 优先）。 */
    private static IgnoreStrategy merged(Deque<IgnoreStrategy> stack) {
        boolean tenantLine = false;
        boolean dynamicTableName = false;
        boolean blockAttack = false;
        boolean illegalSql = false;
        boolean dataPermission = false;
        Map<String, Boolean> others = null;
        for (IgnoreStrategy s : stack) {
            tenantLine |= Boolean.TRUE.equals(s.getTenantLine());
            dynamicTableName |= Boolean.TRUE.equals(s.getDynamicTableName());
            blockAttack |= Boolean.TRUE.equals(s.getBlockAttack());
            illegalSql |= Boolean.TRUE.equals(s.getIllegalSql());
            dataPermission |= Boolean.TRUE.equals(s.getDataPermission());
            if (s.getOthers() != null && !s.getOthers().isEmpty()) {
                if (others == null) {
                    others = new HashMap<>();
                }
                for (Map.Entry<String, Boolean> e : s.getOthers().entrySet()) {
                    others.merge(e.getKey(), e.getValue(), (a, b) -> a || b);
                }
            }
        }
        return IgnoreStrategy.builder()
                .tenantLine(tenantLine)
                .dynamicTableName(dynamicTableName)
                .blockAttack(blockAttack)
                .illegalSql(illegalSql)
                .dataPermission(dataPermission)
                .others(others)
                .build();
    }

    /** 作用域句柄（无状态单例；close = {@link #exit()}）。 */
    public static final class Scope implements AutoCloseable {

        private static final Scope INSTANCE = new Scope();

        private Scope() {
        }

        @Override
        public void close() {
            exit();
        }
    }
}
