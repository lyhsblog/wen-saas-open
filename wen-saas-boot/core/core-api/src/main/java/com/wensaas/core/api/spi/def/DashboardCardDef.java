package com.wensaas.core.api.spi.def;

import com.wensaas.core.api.spi.DashboardCardProvider;

import java.util.List;
import java.util.Objects;

/**
 * 工作台业务数据卡片贡献项（工作台「业务数据卡片区」要素）。
 *
 * <p>业务模块通过 {@code BusinessModuleDescriptor.dashboardCards()} 声明：
 * <pre>
 *   new DashboardCardDef("base.ticket.my_status", "我的工单",
 *       DashboardCardDisplay.LIST, DashboardCardSpan.THIRD,
 *       "/tickets", List.of(), 100, true, TicketDashboardCardProvider.class)
 * </pre>
 *
 * <p>{@code providerClass} 必须是已注册为 Spring Bean 的 {@link DashboardCardProvider} 实现；
 * 租户工作台聚合时通过 {@code ApplicationContext.getBean(providerClass)} 拿到单例求值（同
 * {@code TaskRunner} 取 {@code TaskHandler}）。
 *
 * @param code          卡片编码，按 {@code {module}.{card_name}} 命名，全局唯一
 * @param name          卡片标题
 * @param display       数据展示形式
 * @param span          占位宽度（1/3、1/2、满行）
 * @param actionUrl     点击跳转目标（租户端相对路径，可空表示不可点）
 * @param visibleRoles  可见角色 code 列表；空表示全部角色可见（主管理员恒可见）
 * @param sortWeight    区块内默认排序权重（小在前）
 * @param showOnH5      是否在 H5 端展示
 * @param providerClass 数据提供者实现类
 */
public record DashboardCardDef(
        String code,
        String name,
        DashboardCardDisplay display,
        DashboardCardSpan span,
        String actionUrl,
        List<String> visibleRoles,
        int sortWeight,
        boolean showOnH5,
        Class<? extends DashboardCardProvider> providerClass) {

    public DashboardCardDef {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(span, "span");
        Objects.requireNonNull(providerClass, "providerClass");
        if (code.isBlank()) {
            throw new IllegalArgumentException("卡片 code 不可为空");
        }
        visibleRoles = visibleRoles == null ? List.of() : List.copyOf(visibleRoles);
    }
}
