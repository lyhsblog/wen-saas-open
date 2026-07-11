package com.wensaas.core.impl.notification.inbox;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.notification.inbox.DeliveryDetail;
import com.wensaas.core.api.notification.inbox.InboxItem;
import com.wensaas.core.api.notification.inbox.InboxPage;
import com.wensaas.core.api.notification.inbox.MessageDetailView;
import com.wensaas.core.api.notification.inbox.MessageInboxException;
import com.wensaas.core.api.notification.inbox.MessageInboxService;
import com.wensaas.core.api.notification.inbox.UnreadCount;
import com.wensaas.core.api.notification.inbox.UnreadSummary;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.wensaas.core.impl.notification.delivery.SysMessageTask;
import com.wensaas.core.impl.notification.delivery.SysMessageTaskMapper;

/**
 * {@link MessageInboxService} 实现（站内信收件箱：列表 / 详情 / 已读 / 归档）。
 *
 * <p>行级权限：{@code recipient_id = 当前用户}，全程显式传入 mapper。关联对象「内容不可见」降级由
 * {@link MessageDetailView} 上的注解在序列化阶段完成，本类不写降级判断。
 */
public class MessageInboxServiceImpl implements MessageInboxService {

    private static final int ARCHIVE_DAYS = 90;
    private static final int SUMMARY_LEN = 80;

    private final SysStationMessageMapper stationMapper;
    private final SysMessageTaskMapper taskMapper;

    public MessageInboxServiceImpl(SysStationMessageMapper stationMapper, SysMessageTaskMapper taskMapper) {
        this.stationMapper = stationMapper;
        this.taskMapper = taskMapper;
    }

    @Override
    public InboxPage list(String category, String readState, String keyword, boolean archived, int page, int size) {
        Long me = requireUserId();
        String state = readState == null || readState.isBlank() ? "ALL" : readState;
        LocalDateTime threshold = LocalDateTime.now().minusDays(ARCHIVE_DAYS);
        IPage<SysStationMessage> result = stationMapper.selectInbox(
                new Page<>(page, size), me,
                emptyToNull(category), state, emptyToNull(keyword), threshold, archived);
        List<InboxItem> items = result.getRecords().stream().map(this::toItem).toList();
        return new InboxPage(items, result.getTotal(), page, size);
    }

    @Override
    public MessageDetailView open(Long id) {
        Long me = requireUserId();
        SysStationMessage msg = stationMapper.selectOwned(id, me);
        if (msg == null) {
            throw new MessageInboxException(MessageInboxException.Code.MESSAGE_NOT_FOUND, "消息不存在: " + id);
        }
        boolean read = msg.getReadAt() != null;
        if (!read) {
            stationMapper.markRead(id, me);
            read = true;
        }
        return toDetail(msg, read);
    }

    @Override
    public void markRead(Long id) {
        stationMapper.markRead(id, requireUserId());
    }

    @Override
    public void markAllRead(String category) {
        stationMapper.markAllRead(requireUserId(), emptyToNull(category));
    }

    @Override
    public void delete(Long id) {
        stationMapper.physicalDelete(id, requireUserId());
    }

    @Override
    public UnreadSummary unreadSummary() {
        List<UnreadCountRow> rows = stationMapper.countUnreadByCategory(requireUserId());
        long total = 0;
        List<UnreadCount> byCategory = new ArrayList<>(rows.size());
        for (UnreadCountRow r : rows) {
            total += r.getCount();
            byCategory.add(new UnreadCount(r.getCategory(), r.getCount()));
        }
        return new UnreadSummary(total, byCategory);
    }

    @Override
    public List<DeliveryDetail> deliveries(Long id) {
        Long me = requireUserId();
        SysStationMessage msg = stationMapper.selectOwned(id, me);
        if (msg == null) {
            throw new MessageInboxException(MessageInboxException.Code.MESSAGE_NOT_FOUND, "消息不存在: " + id);
        }
        String taskId = msg.getTaskId();
        if (taskId == null || taskId.isBlank()) {
            // 存量历史站内信无 task_id：兜底「站内信已送达」
            return List.of(new DeliveryDetail("STATION", "SENT", msg.getCreatedAt(), 0, null));
        }
        List<DeliveryDetail> details = new ArrayList<>();
        for (SysMessageTask t : taskMapper.selectByTaskId(taskId)) {
            if (!me.equals(t.getRecipientId())) {
                continue;
            }
            details.add(new DeliveryDetail(t.getChannel(), t.getStatus(), t.getSentAt(),
                    t.getRetryCount() == null ? 0 : t.getRetryCount(), t.getErrorReason()));
        }
        if (details.isEmpty()) {
            details.add(new DeliveryDetail("STATION", "SENT", msg.getCreatedAt(), 0, null));
        }
        return details;
    }

    private InboxItem toItem(SysStationMessage m) {
        return new InboxItem(m.getId(), m.getCategory(), m.getSourceModule(), m.getTitle(),
                summarize(m.getBody()), m.getPriority(), m.getReadAt() != null, m.getCreatedAt());
    }

    private MessageDetailView toDetail(SysStationMessage m, boolean read) {
        MessageDetailView v = new MessageDetailView();
        v.setId(m.getId());
        v.setCategory(m.getCategory());
        v.setSourceModule(m.getSourceModule());
        v.setTitle(m.getTitle());
        v.setBody(m.getBody());
        v.setRelatedType(m.getRelatedType());
        v.setRelatedId(m.getRelatedId());
        v.setLink(m.getLink());
        v.setPriority(m.getPriority());
        v.setRead(read);
        v.setCreatedAt(m.getCreatedAt());
        return v;
    }

    private static String summarize(String body) {
        if (body == null) {
            return null;
        }
        String oneLine = body.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= SUMMARY_LEN ? oneLine : oneLine.substring(0, SUMMARY_LEN) + "…";
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private Long requireUserId() {
        UserPrincipal p = UserContext.get();
        if (p == null) {
            throw new MessageInboxException(MessageInboxException.Code.MESSAGE_NOT_FOUND, "未登录");
        }
        return p.userId();
    }
}
