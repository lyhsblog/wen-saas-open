/**
 * 自定义字段框架对外契约。
 *
 * <p>业务模块通过 {@link com.wensaas.core.api.spi.def.CustomFieldObjectDef} 声明哪些业务对象支持自定义字段；
 * 租户主管理员在字段管理 UI 创建 / 改名 / 索引化 / 软删字段（REST/UI 会话落地）；
 * 业务方写入 / 查询时通过 {@link com.wensaas.core.api.customfield.CustomFieldService} 校验值。
 */
package com.wensaas.core.api.customfield;
