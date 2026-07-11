package com.wensaas.core.web.ops.dto;

import io.swagger.v3.oas.annotations.media.Schema;

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
 * 运营租户详情聚合视图（4 Tab v1：基本 + 主管理员 + 套餐 + 加购席位）。
 *
 * <p>Tab 5 账单/Tab 6 使用情况/Tab 7 操作日志 v1 暂不实现。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class TenantDetailView {
    private TenantBasicView tenant;
    private MainAdminView mainAdmin;
    private List<PlanView> plans;
    private SeatAddonView seatAddons;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class TenantBasicView {
        private String id;
        private String code;
        private String name;
        private String tenantNo;
        private String remark;
        private String csmUserId;
        private String csmName;
        private Integer memberCap;
        private Long storageCap;
        private String expireAt;
        private String timezone;
        private String fullName;
        private String uscc;
        private String industry;
        private String scale;
        private String region;
        private List<String> tags;
        private Integer status;
        private String activationStatus;
        private String createdAt;
        private String lastActiveAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class MainAdminView {
        private String userId;
        private String username;
        private String realName;
        private String mobile;
        private String email;
        @Schema(description = "主管理员账号状态：0=未激活（开通后尚未设密码）/ 1=正常（OPS-T-015）",
                    allowableValues = {"0", "1"})
        private Integer status;
    }

    /**
     * 套餐项；与 {@code /api/v1/me/capabilities} 中的 PackageView 同结构。
     * {@code readonly=true} 表示该套餐已过期且无 active 覆盖。
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class PlanView {
        private String code;
        private String name;
        private String expiresAt;
        private boolean readonly;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class SeatAddonView {
        private int totalQuantity;
        private List<SeatAddonHistoryItemView> history;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class SeatAddonHistoryItemView {
        private String id;
        private int quantity;
        private String remark;
        private String operatorId;
        private String operatorName;
        private String createdAt;
    }
}
