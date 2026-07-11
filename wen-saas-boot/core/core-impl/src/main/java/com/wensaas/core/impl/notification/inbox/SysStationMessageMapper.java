package com.wensaas.core.impl.notification.inbox;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code sys_station_message} Mapper。
 *
 * <p>写入走 {@link com.wensaas.core.impl.notification.adapter.StationChannelAdapter} 的 BaseMapper.insert；
 * 用户侧收件箱检索 / 已读 / 删除走本接口手写 SQL。所有方法显式带 {@code recipient_id}（行级权限双保险，
 * 租户隔离由 MP TenantLine 自动注入）。
 */
@Mapper
public interface SysStationMessageMapper extends BaseMapper<SysStationMessage> {

    /**
     * 收件箱分页。
     *
     * @param category          SYSTEM/BUSINESS/TODO；null 不过滤
     * @param readState         ALL / READ / UNREAD
     * @param keyword           标题 / 正文模糊；null 不过滤
     * @param archivedThreshold 归档时间边界（now - 90 天）
     * @param archived          false=活跃区（created_at >= threshold）；true=归档区（created_at < threshold）
     */
    IPage<SysStationMessage> selectInbox(IPage<SysStationMessage> page,
                                         @Param("recipientId") Long recipientId,
                                         @Param("category") String category,
                                         @Param("readState") String readState,
                                         @Param("keyword") String keyword,
                                         @Param("archivedThreshold") LocalDateTime archivedThreshold,
                                         @Param("archived") boolean archived);

    /** 按 id 取本人消息（行级权限）。 */
    SysStationMessage selectOwned(@Param("id") Long id, @Param("recipientId") Long recipientId);

    /** 单条标已读（仅未读时写入 read_at）。 */
    int markRead(@Param("id") Long id, @Param("recipientId") Long recipientId);

    /** 全部 / 按分类标已读。 */
    int markAllRead(@Param("recipientId") Long recipientId, @Param("category") String category);

    /** 物理删除本人消息（站内信不软删）。 */
    int physicalDelete(@Param("id") Long id, @Param("recipientId") Long recipientId);

    /** 未读分类计数（铃铛）。 */
    List<UnreadCountRow> countUnreadByCategory(@Param("recipientId") Long recipientId);
}
