package com.wensaas.core.api.spi;

import com.wensaas.core.api.spi.def.DashboardCardData;

/**
 * 工作台业务数据卡片的数据提供者 SPI（工作台「业务数据卡片区」核心扩展点）。
 *
 * <p>实现要求（同 {@link com.wensaas.core.api.task.TaskHandler} 的「def 携带 bean 类」模式）：
 * <ul>
 *   <li>{@code @Component} 标注，由业务模块自己的 Spring 上下文管理生命周期</li>
 *   <li>通过 {@code BusinessModuleDescriptor.dashboardCards()} 声明的
 *       {@link com.wensaas.core.api.spi.def.DashboardCardDef#providerClass()} 绑定</li>
 *   <li>{@link #render()} 在当前租户线 + 当前 {@link com.wensaas.core.api.auth.user.UserContext} 下计算卡片数据；
 *       不跨租户（无需 ignore 租户线）</li>
 * </ul>
 *
 * <p>租户工作台聚合时通过 {@code ApplicationContext.getBean(providerClass)} 拿到单例并调用 {@link #render()}；
 * 抛异常时该卡片降级为空态，不影响整页（fail-soft）。
 */
public interface DashboardCardProvider {

    /**
     * 计算当前用户在当前租户下的卡片数据。
     *
     * @return 卡片数据；返回 {@code null} 或全空 {@link DashboardCardData} 视为「无数据」（前端空态）
     */
    DashboardCardData render();
}
