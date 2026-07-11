package com.wensaas.core.api.compliance.spi;

import java.util.List;

/**
 * 可导出对象声明 SPI。各业务模块实现并注册为 Spring Bean，声明自己暴露给
 * 「超量导出审批」开关的导出对象；合规底座聚合所有实现 + 内置基础对象（成员/操作日志/字典），
 * 在策略页按对象呈现「是否需审批」开关。
 *
 * <p>与 {@link DataExportContributor}（全量数据导出的内容贡献者）相互独立：本 SPI 只声明
 * 「哪些导出对象受审批开关治理」，不产出导出内容。
 */
public interface ExportObjectContributor {

    /** 本贡献者声明的可导出对象列表（{@code code} 在全局需唯一，重复以先注册者为准）。 */
    List<ExportObjectDef> exportObjects();
}
