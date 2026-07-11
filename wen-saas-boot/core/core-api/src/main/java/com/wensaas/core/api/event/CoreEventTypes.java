package com.wensaas.core.api.event;

/**
 * 底座自身发布的领域事件 code 与 payload 形态。业务模块经
 * {@code BusinessModuleDescriptor.eventSubscriptions()} 订阅（{@link DomainEventHandler}）。
 */
public final class CoreEventTypes {

    /** 成员被停用。业务模块可借此处理该成员名下的业务数据（如客户改派提醒）。 */
    public static final String MEMBER_DISABLED = "sys.member_disabled";

    private CoreEventTypes() {
    }

    /**
     * {@link #MEMBER_DISABLED} 的 payload。id 序列化为字符串防 JS 消费方丢精度。
     *
     * @param userId     被停用成员 user_id
     * @param realName   被停用成员姓名（发布时快照）
     * @param operatorId 操作人 user_id
     */
    public record MemberDisabledPayload(String userId, String realName, String operatorId) {
    }
}
