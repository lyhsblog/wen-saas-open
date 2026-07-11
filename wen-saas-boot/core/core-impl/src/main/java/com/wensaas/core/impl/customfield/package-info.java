/**
 * 自定义字段框架实现。
 *
 * <p>包结构与字典框架对称：
 * <pre>
 *   CustomFieldProperties / CustomFieldConfiguration  — Spring 装配
 *   SysCustomFieldDef(Mapper) / SysCustomFieldQuota(Mapper) — DB 层
 *   CustomFieldDataLoader / CustomFieldResolver / RedisCustomFieldResolver — 缓存
 *   CustomFieldServiceImpl — 读 API
 *   CustomFieldDefManager — 内部写 API（CRUD，不暴露 REST）
 *   CustomFieldValueValidator — 业务方写入端 helper
 *   CustomFieldQuotaManager — atomic UPDATE 额度增减
 *   CustomFieldIndexExecutor — PG 表达式索引 DDL
 * </pre>
 */
package com.wensaas.core.impl.customfield;
