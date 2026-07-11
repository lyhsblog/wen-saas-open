package com.wensaas.core.impl.notification.preference;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.NotificationCategory;
import com.wensaas.core.api.notification.NotificationPreferenceService;
import com.wensaas.core.api.tenant.CrossTenantQuery;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户通知偏好服务实现。
 *
 * <p>偏好表为混合表，但读写均以显式 (tenant_id, user_id) 过滤、不依赖环境 TenantContext，
 * 故整类 {@code @CrossTenantQuery} 绕过 MP TenantLine 自动注入。
 *
 * <p>{@link #GRID_CHANNELS} 为个人中心偏好网格暴露的通道（站内信强制 + 邮件 + 短信）；
 * {@code SSO 推送} 为产品占位通道，无对应 {@link Channel}，暂不纳入。
 */
@CrossTenantQuery("NotificationPreferenceService: 按显式 tenant_id 过滤，无需 TenantLine")
public class NotificationPreferenceServiceImpl implements NotificationPreferenceService {

    static final List<Channel> GRID_CHANNELS = List.of(Channel.STATION, Channel.EMAIL, Channel.SMS);

    private final SysUserNotificationPreferenceMapper mapper;

    public NotificationPreferenceServiceImpl(SysUserNotificationPreferenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean isChannelEnabled(long tenantId, long userId, NotificationCategory category, Channel channel) {
        if (channel == Channel.STATION) {
            return true; // 站内信强制送达
        }
        SysUserNotificationPreference row = mapper.selectOne(query(tenantId, userId)
                .eq(SysUserNotificationPreference::getCategory, category.name())
                .eq(SysUserNotificationPreference::getChannel, channel.name()));
        // 无记录 = 默认开启
        return row == null || Boolean.TRUE.equals(row.getEnabled());
    }

    @Override
    public List<PreferenceItem> getForUser(long tenantId, long userId) {
        List<SysUserNotificationPreference> rows = mapper.selectList(query(tenantId, userId));
        Map<String, Boolean> existing = new HashMap<>();
        for (SysUserNotificationPreference r : rows) {
            existing.put(r.getCategory() + "/" + r.getChannel(), Boolean.TRUE.equals(r.getEnabled()));
        }
        List<PreferenceItem> grid = new ArrayList<>();
        for (NotificationCategory category : NotificationCategory.values()) {
            for (Channel channel : GRID_CHANNELS) {
                boolean enabled = channel == Channel.STATION
                        || existing.getOrDefault(category.name() + "/" + channel.name(), true);
                grid.add(new PreferenceItem(category, channel, enabled));
            }
        }
        return grid;
    }

    @Override
    public void update(long tenantId, long userId, List<PreferenceItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (PreferenceItem item : items) {
            if (item.channel() == Channel.STATION) {
                continue; // 站内信强制开启，忽略关闭请求
            }
            SysUserNotificationPreference existing = mapper.selectOne(query(tenantId, userId)
                    .eq(SysUserNotificationPreference::getCategory, item.category().name())
                    .eq(SysUserNotificationPreference::getChannel, item.channel().name()));
            if (existing == null) {
                SysUserNotificationPreference row = new SysUserNotificationPreference();
                row.setTenantId(tenantId);
                row.setUserId(userId);
                row.setCategory(item.category().name());
                row.setChannel(item.channel().name());
                row.setEnabled(item.enabled());
                row.setCreatedAt(now);
                row.setUpdatedAt(now);
                mapper.insert(row);
            } else if (!Boolean.valueOf(item.enabled()).equals(existing.getEnabled())) {
                existing.setEnabled(item.enabled());
                existing.setUpdatedAt(now);
                mapper.updateById(existing);
            }
        }
    }

    private LambdaQueryWrapper<SysUserNotificationPreference> query(long tenantId, long userId) {
        return Wrappers.<SysUserNotificationPreference>lambdaQuery()
                .eq(SysUserNotificationPreference::getTenantId, tenantId)
                .eq(SysUserNotificationPreference::getUserId, userId);
    }
}
