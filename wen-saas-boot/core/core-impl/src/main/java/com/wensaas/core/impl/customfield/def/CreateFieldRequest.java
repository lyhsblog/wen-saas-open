package com.wensaas.core.impl.customfield.def;

import com.wensaas.core.api.customfield.CustomFieldType;

/**
 * 创建自定义字段的内部入参。仅供 {@link CustomFieldDefManager#createField} 使用，
 * REST DTO 等 REST 会话再单独建。
 *
 * @param tenantId      所属租户
 * @param objectCode    业务对象 code
 * @param fieldCode     字段 code（v1 接受同 PG 标识符正则：{@code [a-z_][a-z0-9_]{0,62}}，
 *                      因要进表达式索引 DDL）
 * @param fieldName     显示名
 * @param type          字段类型
 * @param required      是否必填
 * @param indexed       是否启用表达式索引（occupies index_quota）
 * @param defaultValue  默认值（v1 不做"写入时自动填充"，仅存元数据）
 * @param refDictCode   ENUM 必填：引用字典分组 code
 * @param refObjectCode REF 必填：引用业务对象 code
 * @param displayOrder  显示顺序（同对象下排序）
 */
public record CreateFieldRequest(long tenantId,
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
