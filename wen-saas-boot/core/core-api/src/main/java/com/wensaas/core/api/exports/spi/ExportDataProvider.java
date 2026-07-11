package com.wensaas.core.api.exports.spi;

import java.util.List;
import java.util.Map;

/**
 * 业务数据导出内容提供者 SPI。各业务模块实现并注册为
 * Spring Bean，声明某个导出对象的列结构与取数逻辑；导出执行框架（{@code ExportTaskHandler}）
 * 在<b>申请人</b>的 {@code UserContext} 内调用 {@link #export}，已注册数据权限的表自动按申请人
 * data_scope 过滤——所见即所得，审批只控制「能不能导」、不放大数据范围。
 *
 * <p>{@link #objectCode()} 必须与 {@code ExportObjectContributor} 声明的导出对象 code 一致，
 * 二者共同构成一个完整的导出对象：前者管「内容怎么取」，后者管「是否需要审批」。
 *
 * <p>与 {@code DataExportContributor}（客户全量数据导出）相互独立。
 */
public interface ExportDataProvider {

    /** 导出对象 code（与 {@code ExportObjectContributor} 声明对齐，如 member / audit_log / dict）。 */
    String objectCode();

    /** 发起导出所需的功能权限点（null 表示仅需登录）。提交时校验申请人持有，否则 403。 */
    String requiredPermission();

    /** 导出列（表头顺序即写行顺序）。 */
    List<ExportColumn> columns();

    /** 预估条数（按 filters 在申请人上下文 count；仅作申请信息展示，不参与审批判定）。 */
    long estimateCount(Map<String, String> filters);

    /**
     * 按 filters 取数并逐行写入 {@code sink}。运行在申请人 {@code UserContext} +
     * 提交方租户上下文内；应分页拉取避免整表入内存，并在循环边界检查 {@link ExportSink#stopped()}。
     */
    void export(Map<String, String> filters, ExportSink sink);
}
