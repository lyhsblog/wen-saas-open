package com.wensaas.core.web.ops.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 运营租户列表单行视图。
 *
 * <p><b>status vs activationStatus（OPS-T-016）</b>：{@code status} 是租户开关闸（0 暂停 / 1 正常 / 2 已过期），
 * 由运营操作或到期任务驱动；{@code activationStatus} 是面向 Ui 的派生综合态，叠加了主管理员是否已激活——
 * status=2→expired、status=0→paused、status=1 时再看主管理员 sys_user.status（0→pending_activation / 1→activated）。
 * 前端徽章用 activationStatus，暂停/过期判定用 status。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class TenantListItemView {
    private String id;
    @Schema(description = "路由位：独立域名路径段，全平台唯一，开通后不可改（OPS-T-003）")
    private String code;
    private String name;
    @Schema(description = "内部编号：运营可见的平台唯一编号，系统生成（OPS-T-003），与 code 角色不同")
    private String tenantNo;
    private String adminUsername;
    private String csmUserId;
    private String csmName;
    @Schema(description = "租户状态：0=暂停 / 1=正常 / 2=已过期（OPS-T-014/016）", allowableValues = {"0", "1", "2"})
    private Integer status;
    @Schema(description = "派生激活态（OPS-T-014）",
                allowableValues = {"paused", "pending_activation", "activated", "expired", "unknown"})
    private String activationStatus;
    private String createdAt;
    private String lastActiveAt;
    private int planCount;
    private java.util.List<String> planCodes;
    private String nearestExpireAt;
    private int seatAddonTotal;
}
