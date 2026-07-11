package com.wensaas.core.api.tenant.selfservice;

import com.wensaas.core.api.spi.def.DashboardCardData;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 租户后台 · 工作台首页聚合。
 *
 * <p>面向租户内任意已登录成员，主体取自 {@code UserContext}，无需额外权限点（自助首页）。
 * 与运营侧 {@code OpsDashboardService} 平行，但不跨租户（走当前租户线）。
 *
 * <p>{@link #overview()} 提供固定区域（顶部信息条 + 套餐到期 + 待办/消息/公告 三卡）；
 * {@link #businessCards()} 解析各业务模块通过 {@code BusinessModuleDescriptor.dashboardCards()}
 * 贡献的业务数据卡片（核心扩展点）。
 */
public interface TenantWorkbenchService {

    /** 顶部信息条 + 固定三卡（待办 / 消息 / 公告）。 */
    OverviewView overview();

    /** 业务数据卡片区：解析已注册卡片定义，按可见角色过滤、排序、逐个求值。 */
    List<BusinessCardView> businessCards();

    // ===== records =====

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class OverviewView {
        private Greeting greeting;
        private /** 套餐到期提醒；仅主管理员可见，非主管理员或无到期日时为 null。 */
            PlanExpiry planExpiry;
        private TodoCard todo;
        private MessageCard message;
        private AnnouncementCard announcement;

    }

    /** 顶部欢迎语。 */
    record Greeting(String userName, String roleName) {
    }

    /**
     * 套餐到期提醒。
     *
     * @param severity {@code red}（≤3 天）/ {@code yellow}（≤7 天）/ {@code none}
     */
    record PlanExpiry(String planName, Instant expireAt, long daysLeft, String severity) {
    }

    /** 我的待办卡片。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class TodoCard {
        private long total;
        private List<TodoEntry> items;

    }

    record TodoEntry(String label, long count, String actionUrl) {
    }

    /** 我的消息卡片。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class MessageCard {
        private long unreadTotal;
        private List<MessageBrief> recent;

    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class MessageBrief {
        private Long id;
        private String title;
        private String category;
        private LocalDateTime time;

    }

    /** 系统公告卡片。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class AnnouncementCard {
        private long total;
        private AnnouncementBrief latest;

    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class AnnouncementBrief {
        private String title;
        private String from;
        private LocalDateTime time;

    }

    /**
     * 业务数据卡片（定义元信息 + 运行时数据）。
     *
     * @param display {@code NUMBER} / {@code NUMBER_TREND} / {@code LIST} / {@code CHART}
     * @param span    {@code THIRD} / {@code HALF} / {@code FULL}
     * @param data    provider 求值结果；provider 异常或无数据时为 null（前端空态）
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class BusinessCardView {
        private String code;
        private String moduleCode;
        private String name;
        private String display;
        private String span;
        private String actionUrl;
        private int sortWeight;
        private boolean showOnH5;
        private DashboardCardData data;

    }
}
