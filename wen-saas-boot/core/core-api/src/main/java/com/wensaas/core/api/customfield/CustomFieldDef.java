package com.wensaas.core.api.customfield;

/**
 * 自定义字段定义对外查询契约。
 *
 * @param id            字段 ID（雪花）
 * @param tenantId      所属租户
 * @param objectCode    业务对象 code（对应 {@link com.wensaas.core.api.spi.def.CustomFieldObjectDef#code()}）
 * @param fieldCode     字段 code（JSON key，租户内同 object 唯一）
 * @param fieldName     显示名（可改）
 * @param type          字段类型（不可改）
 * @param required      是否必填
 * @param indexed       是否启用表达式索引
 * @param defaultValue  默认值（v1 仅作为元数据保留，未做"写入时自动填充"）
 * @param refDictCode   ENUM 类型时引用的字典分组 code（其它类型为 null）
 * @param refObjectCode REF 类型时引用的业务对象 code（其它类型为 null）
 * @param displayOrder  显示顺序
 */
public record CustomFieldDef(Long id,
                              Long tenantId,
                              String objectCode,
                              String fieldCode,
                              String fieldName,
                              CustomFieldType type,
                              boolean required,
                              boolean indexed,
                              String defaultValue,
                              String refDictCode,
                              String refObjectCode,
                              int displayOrder) {
}
