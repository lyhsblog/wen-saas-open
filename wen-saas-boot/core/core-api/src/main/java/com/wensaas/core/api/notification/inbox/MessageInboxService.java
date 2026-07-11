package com.wensaas.core.api.notification.inbox;

import java.util.List;
import com.wensaas.core.api.notification.access.RelatedObjectGuarded;

/**
 * 站内信收件箱（用户视角）API。
 *
 * <p>行级权限：所有操作均按当前登录用户（{@code recipient_id}）隔离，看不到他人消息。
 * 关联对象「内容不可见」降级由 {@link MessageDetailView} 上的 {@code @RelatedObjectGuarded} 注解在
 * 序列化阶段自动完成，service 不写降级逻辑。
 */
public interface MessageInboxService {

    /**
     * 收件箱分页。
     *
     * @param category  SYSTEM/BUSINESS/TODO；null/空不过滤
     * @param readState ALL / READ / UNREAD
     * @param keyword   标题 / 正文模糊；null 不过滤
     * @param archived  false=活跃区（近 90 天）；true=归档区（90 天前）
     */
    InboxPage list(String category, String readState, String keyword, boolean archived, int page, int size);

    /** 打开消息详情，未读则同时标已读。 */
    MessageDetailView open(Long id);

    /** 单条标已读。 */
    void markRead(Long id);

    /** 全部 / 按分类标已读（category 为 null 表示全部）。 */
    void markAllRead(String category);

    /** 删除单条（物理删）。 */
    void delete(Long id);

    /** 未读汇总（铃铛：总数 + 分类）。 */
    UnreadSummary unreadSummary();

    /** 投递明细（按 task 关联各通道结果）。 */
    List<DeliveryDetail> deliveries(Long id);
}
