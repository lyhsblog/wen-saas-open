/**
 * Web 层公共契约：分页 record、JSON 序列化定制、统一异常 Advice、CORS 装配。
 *
 * <p>所有 v1+ Controller 共用本包提供的 {@link com.wensaas.core.web.common.PageQuery} /
 * {@link com.wensaas.core.web.common.PageResponse}；雪花 ID JSON-string + 北京时间 +08:00 由
 * {@link com.wensaas.core.web.common.JsonConfig} 注入。
 */
package com.wensaas.core.web.common;
