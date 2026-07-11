package com.wensaas.core.api.compliance.spi;

/**
 * 可导出对象声明（业务模块经 {@link ExportObjectContributor} 注册）。
 *
 * <p>「超量导出审批」由「该导出对象是否需审批」开关驱动（不按数量阈值评估）。每个对象由业务方
 * 声明唯一 {@code code} 与默认是否需审批；主管理员可在合规策略页按租户覆盖该开关。
 *
 * @param code                    对象机器码（租户内全局唯一，如 {@code member} / {@code audit_log}）
 * @param name                    对象显示名（如「成员列表」「操作日志」）
 * @param module                  所属模块码（用于分组展示，如 {@code member} / {@code audit}）
 * @param defaultRequiresApproval 默认是否需审批（租户未单独配置时回退此值）
 */
public record ExportObjectDef(String code, String name, String module, boolean defaultRequiresApproval) {
}
