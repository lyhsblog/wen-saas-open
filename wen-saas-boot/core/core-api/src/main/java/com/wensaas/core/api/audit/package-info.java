/**
 * 底座操作日志契约。
 *
 * <p>对业务方暴露：
 * <ul>
 *   <li>{@link com.wensaas.core.api.audit.Auditable} 注解：方法级"自动记录"</li>
 *   <li>{@link com.wensaas.core.api.audit.AuditLogRecorder}：主动记录入口</li>
 *   <li>{@link com.wensaas.core.api.audit.Sensitive} + {@link com.wensaas.core.api.audit.SensitiveType}：DTO 脱敏</li>
 *   <li>{@link com.wensaas.core.api.audit.SensitiveHandler}：自定义脱敏 SPI</li>
 * </ul>
 */
package com.wensaas.core.api.audit;
