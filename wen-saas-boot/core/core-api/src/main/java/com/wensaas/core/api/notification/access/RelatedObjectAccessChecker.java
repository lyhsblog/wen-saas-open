package com.wensaas.core.api.notification.access;

/**
 * 关联对象访问权限 SPI。
 *
 * <p>站内信的 {@code related_type / related_id} 在发送时固化入库，不随收件人权限变化更新。
 * 为避免成员调岗后在消息中心看到已无权访问的对象信息，渲染（序列化）阶段对带
 * {@code @RelatedObjectGuarded} 的 DTO 二次校验：无权时正文降级、跳转链接置空。
 *
 * <p>业务模块为自己的 {@code relatedType} 注册一个 {@code @Component} 实现；未注册默认放行
 * （与 {@link FilePermissionChecker} 同模式，业务模块按需接入）。同一 {@code relatedType}
 * 只允许一个 checker（启动期重复抛 {@link IllegalStateException}）。
 *
 * <p>契约：
 * <ul>
 *   <li>{@link #relatedType()} 必须与 {@code sys_station_message.related_type} 列值一致</li>
 *   <li>{@link #canAccess} 仅返回 true/false；实现应只读 + 幂等</li>
 *   <li><b>租户隔离由实现内部负责</b>（典型走 {@code TenantContext.require()}）——序列化阶段
 *   无法判定租户，evaluator/序列化器不触碰租户</li>
 * </ul>
 *
 * <p>示例（业务模块内）：
 * <pre>{@code
 * @Component
 * public class CustomerRelatedObjectChecker implements RelatedObjectAccessChecker {
 *     private final CustomerService service;
 *
 *     @Override public String relatedType() { return "Customer"; }
 *
 *     @Override
 *     public boolean canAccess(String relatedId, Long currentUserId) {
 *         return service.canAccess(currentUserId, relatedId);
 *     }
 * }
 * }</pre>
 */
public interface RelatedObjectAccessChecker {

    /** 处理的关联类型（与 {@code sys_station_message.related_type} 列值一致）。 */
    String relatedType();

    /**
     * 当前用户是否可访问该关联对象。
     *
     * @param relatedId     关联业务对象 ID（字符串，兼容非雪花 ID）
     * @param currentUserId 当前用户 ID；可能为 null（无会话上下文时）
     */
    boolean canAccess(String relatedId, Long currentUserId);
}
