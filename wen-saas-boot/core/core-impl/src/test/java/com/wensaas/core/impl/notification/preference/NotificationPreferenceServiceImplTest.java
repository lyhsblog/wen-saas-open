package com.wensaas.core.impl.notification.preference;

import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.NotificationCategory;
import com.wensaas.core.api.notification.NotificationPreferenceService.PreferenceItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationPreferenceServiceImplTest {

    private SysUserNotificationPreferenceMapper mapper;
    private NotificationPreferenceServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(SysUserNotificationPreferenceMapper.class);
        service = new NotificationPreferenceServiceImpl(mapper);
    }

    @Test
    void isChannelEnabled_station_alwaysTrue_noQuery() {
        boolean enabled = service.isChannelEnabled(0L, 1L, NotificationCategory.SECURITY_ALERT, Channel.STATION);
        assertThat(enabled).isTrue();
        verify(mapper, never()).selectOne(any());
    }

    @Test
    void isChannelEnabled_email_noRow_defaultsTrue() {
        when(mapper.selectOne(any())).thenReturn(null);
        assertThat(service.isChannelEnabled(0L, 1L, NotificationCategory.PACKAGE_EXPIRY, Channel.EMAIL)).isTrue();
    }

    @Test
    void isChannelEnabled_email_rowDisabled_returnsFalse() {
        SysUserNotificationPreference row = new SysUserNotificationPreference();
        row.setEnabled(false);
        when(mapper.selectOne(any())).thenReturn(row);
        assertThat(service.isChannelEnabled(0L, 1L, NotificationCategory.PACKAGE_EXPIRY, Channel.EMAIL)).isFalse();
    }

    @Test
    void getForUser_buildsFullGrid_stationAlwaysEnabled() {
        when(mapper.selectList(any())).thenReturn(List.of());
        List<PreferenceItem> grid = service.getForUser(0L, 1L);

        int expected = NotificationCategory.values().length * NotificationPreferenceServiceImpl.GRID_CHANNELS.size();
        assertThat(grid).hasSize(expected);
        assertThat(grid).filteredOn(i -> i.channel() == Channel.STATION)
                .allMatch(PreferenceItem::enabled);
    }

    @Test
    void update_ignoresStationChannel() {
        service.update(0L, 1L, List.of(
                new PreferenceItem(NotificationCategory.SYSTEM_ANNOUNCEMENT, Channel.STATION, false)));
        // STATION 被忽略：既不查也不写
        verify(mapper, never()).selectOne(any());
        verify(mapper, never()).insert(any(SysUserNotificationPreference.class));
    }
}
