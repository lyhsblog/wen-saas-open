package com.wensaas.core.api.tenant.selfservice;

import java.time.Instant;
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
 * 租户后台 · 系统管理 · 套餐与账号。
 *
 * <p>纯只读「账户面板」：展示运营在运营后台对该租户做的套餐 / 加购操作结果。无任何写操作；
 * 续费 / 加购走运营线下。数据全部来自当前租户自身快照（{@code sys_tenant.plans} / 加购记录 /
 * 操作日志），主管理员看到的与运营在租户详情看到的一致，只是少了「操作」按钮。
 */
public interface TenantAccountService {

    /** 账户概况 + 套餐列表 + 加购汇总 + 客服联系信息（聚合）。 */
    AccountOverview getOverview();

    /** 变更历史（来自运营操作日志，按 target_tenant_id + action 白名单，时间倒序，分页）。 */
    ChangeHistoryPage listChangeHistory(int page, int pageSize);

    // ===================== records =====================

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class AccountOverview {
        private String tenantId;
        private String tenantNo;
        private int packageCount;
        private int expiringSoonCount;
        private Integer baseSeats;
        private int addonSeats;
        private long usedSeats;
        private Instant nearestExpireAt;
        private List<PlanItem> plans;
        private SupportContact support;
    }

    /**
     * 套餐项。
     *
     * @param expiresAt null=永久有效
     * @param readonly  true=已过期（数据只读保留）
     * @param systemDefault true=基础包（系统标配）
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class PlanItem {
        private String packageCode;
        private String name;
        private Instant expiresAt;
        private boolean readonly;
        private boolean systemDefault;
        private List<PlanModule> modules;
    }

    record PlanModule(String moduleCode, String moduleName) {}

    /** 平台运营方联系方式（运营后台全局配置；P0 由 application.yml 配置）。 */
    record SupportContact(String phone, String email) {}

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class ChangeHistoryItem {
        private String id;
        private String action;
        private String objectType;
        private String objectDesc;
        private String operatorName;
        private Instant createdAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class ChangeHistoryPage {
        private List<ChangeHistoryItem> items;
        private long total;
        private int page;
        private int pageSize;
    }
}
