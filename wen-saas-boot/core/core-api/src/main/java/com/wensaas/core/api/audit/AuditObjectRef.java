package com.wensaas.core.api.audit;

/**
 * ② 操作日志的一个主对象引用（写入 {@code sys_operation_log.object_refs} 数组）。
 *
 * @param type 对象类型（中文，如"租户""角色"，与 {@code @Auditable.objectType} 同惯例）
 * @param id   对象 ID（字符串，兼容雪花/非 BIGINT）
 * @param name 友好名快照（③ 给不了的语义；可空）
 */
public record AuditObjectRef(String type, String id, String name) {
}
