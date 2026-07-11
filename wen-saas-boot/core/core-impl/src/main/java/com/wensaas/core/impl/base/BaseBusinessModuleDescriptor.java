package com.wensaas.core.impl.base;

import com.wensaas.core.api.spi.BusinessModule;
import com.wensaas.core.api.spi.BusinessModuleDescriptor;
import com.wensaas.core.api.spi.ModuleMetadata;
import com.wensaas.core.api.spi.def.DashboardCardDef;
import com.wensaas.core.api.spi.def.DashboardCardDisplay;
import com.wensaas.core.api.spi.def.DashboardCardSpan;
import com.wensaas.core.api.spi.def.ImportEntityDef;
import com.wensaas.core.api.spi.def.PermissionDef;
import com.wensaas.core.api.spi.def.TaskHandlerDef;
import com.wensaas.core.impl.compliance.export.DataExportTaskHandler;
import com.wensaas.core.impl.exports.ExportTaskHandler;
import com.wensaas.core.impl.imports.builtin.DeptImportTaskHandler;
import com.wensaas.core.impl.imports.builtin.DictImportTaskHandler;
import com.wensaas.core.impl.imports.builtin.MemberImportTaskHandler;
import com.wensaas.core.impl.imports.builtin.SysImportEntities;
import com.wensaas.core.impl.ticket.spi.TicketDashboardCardProvider;

import java.util.List;

/**
 * 底座"虚拟"业务模块：横切底座能力（数据导入导出 / 合规审批 / 平台工单）的统一 SPI 身份。
 *
 * <p>底座能力借 SPI 机制注册扩展点（权限点 / 工作台卡片 / 导入实体 / 任务处理器），
 * 供角色权限赋值树、任务中心、工作台等统一消费；运营"业务模块"页因此只见
 * 「base + 真业务模块」两类条目。表归属：工单持有 {@code sys_ticket_*}、合规/导入导出
 * 复用既有 {@code sys_*} 表，均为底座保留前缀，{@code ModuleTableValidator} 跳过。
 *
 * <p>{@link #apiPathPrefixes()} 返回空：底座能力常驻、不按套餐拦截、不进套餐候选
 * （{@code PackageAdminServiceImpl.listAvailableModules} 以"非空前缀"判别）。端点分散在
 * {@code /api/v1/imports} / {@code /api/v1/exports} / {@code /api/v1/base/compliance} /
 * {@code /api/v1/base/tickets} 等。运营侧权限点 {@code ops.compliance.*} / {@code ops.ticket.*}
 * 在 {@code OpsPermissionContributor} 注册；底座租户域其余权限点在
 * {@code BasePermissionContributor} 注册（owner 同为 {@code base}，角色树同组展示）。
 *
 * <p>taskHandler / importEntity 的 code 不受 {@code NamespaceValidator} 命名空间校验，
 * 保持 {@code sys.*} / {@code compliance.task.*} 原值。
 */
@BusinessModule
public class BaseBusinessModuleDescriptor implements BusinessModuleDescriptor {

    @Override
    public ModuleMetadata metadata() {
        return new ModuleMetadata(
                "base",
                "底座",
                "横切底座能力：数据导入导出 / 合规审批 / 平台工单；表用 sys_* 底座前缀",
                "1.0.0",
                null,
                List.of());
    }

    @Override
    public List<String> apiPathPrefixes() {
        return List.of();
    }

    @Override
    public List<PermissionDef> permissions() {
        return List.of(
                new PermissionDef("base.compliance.export.approve", "数据合规 · 超量导出审批",
                        "默认无；策略为「持权限的角色」时，赋权后可审批本租户超量导出申请（如合规专员）"),
                new PermissionDef("base.compliance.config", "数据合规 · 策略配置",
                        "查看 / 调整超量导出阈值、审批权限、超时提醒"),
                new PermissionDef("base.ticket.view_all", "工单 · 查看本租户全部工单",
                        "默认无；赋权后可在租户侧查看本租户所有成员的工单")
        );
    }

    /**
     * 工作台业务数据卡片：「我的工单」。全角色可见、点击跳「我的工单」页；
     * 数据由 {@link TicketDashboardCardProvider} 实时计算。
     */
    @Override
    public List<DashboardCardDef> dashboardCards() {
        return List.of(
                new DashboardCardDef(
                        "base.ticket.my_status", "我的工单",
                        DashboardCardDisplay.LIST, DashboardCardSpan.THIRD,
                        "/tickets", List.of(), 100, true,
                        TicketDashboardCardProvider.class)
        );
    }

    /** 底座内置导入实体（部门 / 成员 / 字典），定义见 {@link SysImportEntities}。 */
    @Override
    public List<ImportEntityDef> importEntities() {
        return List.of(SysImportEntities.dept(), SysImportEntities.member(), SysImportEntities.dict());
    }

    /**
     * 底座任务处理器：导入 ×3 / 业务数据导出 / 客户全量数据导出（GDPR）。
     * handler bean 分别由 {@code SysImportConfiguration} / {@code ExportConfiguration} /
     * {@code ComplianceConfiguration} 装配，TaskRunner 经 {@code ApplicationContext.getBean} 取用。
     */
    @Override
    public List<TaskHandlerDef> taskHandlers() {
        return List.of(
                new TaskHandlerDef(SysImportEntities.DEPT_HANDLER, DeptImportTaskHandler.class),
                new TaskHandlerDef(SysImportEntities.MEMBER_HANDLER, MemberImportTaskHandler.class),
                new TaskHandlerDef(SysImportEntities.DICT_HANDLER, DictImportTaskHandler.class),
                new TaskHandlerDef(ExportTaskHandler.HANDLER_CODE, ExportTaskHandler.class),
                new TaskHandlerDef(DataExportTaskHandler.HANDLER_CODE, DataExportTaskHandler.class));
    }
}
