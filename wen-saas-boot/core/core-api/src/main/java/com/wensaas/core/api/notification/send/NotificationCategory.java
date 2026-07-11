package com.wensaas.core.api.notification.send;

/**
 * 通知类别（用户级偏好维度）。与运营个人中心「通知偏好」的 6 类别一一对应。
 *
 * <p>与 {@link MessageCategory}（站内信三分类 SYSTEM/BUSINESS/TODO）正交：
 * MessageCategory 决定站内信归档分类，NotificationCategory 决定"用户对该类通知是否在某通道接收"。
 *
 * <p>通知模板可声明所属类别（{@code sys_notification_template.notification_category}）。
 * 投递时按 (用户, 类别, 通道) 查 {@code sys_user_notification_preference} 决定是否跳过；
 * 未声明类别的模板不参与偏好过滤。站内信（{@link Channel#STATION}）强制送达，不可关闭。
 */
public enum NotificationCategory {

    /** 待办与任务：@我、待审批。 */
    TODO_TASK("待办与任务"),
    /** 工单告警：工单超 SLA、新工单分派给我。 */
    TICKET_ALERT("工单告警"),
    /** 客户健康度：我跟进的租户进入"危险"。 */
    CUSTOMER_HEALTH("客户健康度"),
    /** 套餐到期：我开通/跟进的租户临近到期。 */
    PACKAGE_EXPIRY("套餐到期"),
    /** 系统公告：平台公告与维护通知。 */
    SYSTEM_ANNOUNCEMENT("系统公告"),
    /** 安全提醒：新设备登录 / 密码变更。 */
    SECURITY_ALERT("安全提醒");

    private final String label;

    NotificationCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
