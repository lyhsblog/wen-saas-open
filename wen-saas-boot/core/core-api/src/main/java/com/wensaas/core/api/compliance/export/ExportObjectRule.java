package com.wensaas.core.api.compliance.export;

/**
 * 导出对象的审批开关视图（策略页一行）。
 *
 * <p>由业务方声明的 {@link com.wensaas.core.api.compliance.spi.ExportObjectDef} 与当前租户的开关
 * 覆盖合并而来；{@code requiresApproval} 为生效值（租户已配置则用其值，否则回退声明默认值）。
 *
 * @param code             对象机器码
 * @param name             对象显示名
 * @param module           所属模块码
 * @param requiresApproval 生效的「是否需审批」开关
 */
public record ExportObjectRule(String code, String name, String module, boolean requiresApproval) {
}
