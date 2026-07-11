package com.wensaas.core.api.customfield;

/**
 * 自定义字段宿主对象对外查询契约。
 *
 * <p>由 {@link com.wensaas.core.api.spi.def.CustomFieldObjectDef} 在启动期注册到内存 Registry，
 * {@link CustomFieldService#listObjects()} 暴露给字段管理 UI / 业务方枚举。
 *
 * @param code            业务对象 code
 * @param name            显示名
 * @param tableName       物理表名
 * @param extraColumnName jsonb 列名
 * @param description     描述（可空）
 */
public record CustomFieldObject(String code,
                                 String name,
                                 String tableName,
                                 String extraColumnName,
                                 String description) {
}
