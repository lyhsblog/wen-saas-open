package com.wensaas.core.api.spi;

import com.wensaas.core.api.spi.def.CustomFieldObjectDef;
import com.wensaas.core.api.spi.def.DashboardCardDef;
import com.wensaas.core.api.spi.def.DataPermissionTableDef;
import com.wensaas.core.api.spi.def.DictGroupDef;
import com.wensaas.core.api.spi.def.EventSubscription;
import com.wensaas.core.api.spi.def.H5PageDef;
import com.wensaas.core.api.spi.def.ImportEntityDef;
import com.wensaas.core.api.spi.def.NotificationTemplateDef;
import com.wensaas.core.api.spi.def.PackageDef;
import com.wensaas.core.api.spi.def.PermissionDef;
import com.wensaas.core.api.spi.def.TaskHandlerDef;

import java.util.List;

/**
 * 业务模块描述符。
 *
 * <p>业务模块在自己的 maven 工程内提供一个实现该接口的 Bean（通常加 {@link BusinessModule} 注解），
 * 底座在启动时通过 Spring ApplicationContext 收集所有该接口的 Bean，依次校验与注册。
 */
public interface BusinessModuleDescriptor {

    ModuleMetadata metadata();

    /**
     * 该业务模块对外暴露的 HTTP API 路径前缀列表。
     *
     * <p>默认值为 {@code ["/api/v1/{code}/"]}，即约定前缀。模块可覆盖以声明多个或自定义前缀
     * （如同时暴露 {@code /api/v1/crm/} 和 {@code /api/h5/crm/}）。真业务模块按命名空间规范
     * 显式覆写为 {@code /api/v1/biz/{code}/}（样板模块 {@code crm_demo} 即是）。
     *
     * <p>底座在启动期通过 {@code BusinessModuleRegistrar} 将这些前缀注册到
     * {@code ModuleApiRouteRegistry}，供 {@code PackageStatusWebFilter} 做权限拦截。
     * 前缀冲突（不同模块声明相同前缀）会阻断启动。
     */
    default List<String> apiPathPrefixes() {
        return List.of("/api/v1/" + metadata().code() + "/");
    }

    default List<PermissionDef> permissions() {
        return List.of();
    }

    default List<DictGroupDef> dictGroups() {
        return List.of();
    }

    default List<DashboardCardDef> dashboardCards() {
        return List.of();
    }

    default List<H5PageDef> h5Pages() {
        return List.of();
    }

    default List<NotificationTemplateDef> notificationTemplates() {
        return List.of();
    }

    default List<ImportEntityDef> importEntities() {
        return List.of();
    }

    default List<EventSubscription> eventSubscriptions() {
        return List.of();
    }

    default List<CustomFieldObjectDef> customFieldObjects() {
        return List.of();
    }

    /**
     * 声明哪些业务表参与数据权限过滤。
     *
     * <p>v2 取向是"接口尽量不动"，但 {@code creatorColumn} / {@code deptColumn} 无法从 entity 推断，
     * 因此 v1 权限引擎以 default 方法形式新增一个声明点；不实现的模块视为不参与数据权限。
     */
    default List<DataPermissionTableDef> dataPermissionTables() {
        return List.of();
    }

    /**
     * 声明业务模块提供的任务处理器。
     *
     * <p>handlerClass 必须是已注册为 Spring Bean 的 {@link com.wensaas.core.api.task.TaskHandler} 实现；
     * TaskRunner 在执行时通过 ApplicationContext.getBean(handlerClass) 拿到单例。
     *
     * <p>这是接口的第 2 处 default 扩展（同 {@link #dataPermissionTables()}）：因为 handler 实例需要
     * 业务模块自己声明 + 拿到 Spring Bean 引用，无法通过纯扫描机制推断。
     */
    default List<TaskHandlerDef> taskHandlers() {
        return List.of();
    }

    /**
     * 声明业务模块的预置套餐包（开箱即用：模块装上后无需运营手工建包即可授予租户）。
     *
     * <p>code 按 {@code {module}.{pkg_name}} 命名空间约束（启动期校验）；启动期以
     * insert-if-missing 语义落库，已存在的套餐整行不动（运营在套餐管理页的修改是权威），
     * 详见 {@link PackageDef}。
     */
    default List<PackageDef> packages() {
        return List.of();
    }
}
