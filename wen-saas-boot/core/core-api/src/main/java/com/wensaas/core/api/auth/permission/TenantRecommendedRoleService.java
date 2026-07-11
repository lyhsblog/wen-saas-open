package com.wensaas.core.api.auth.permission;

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
 * 租户侧推荐角色（读 + 启用）API。
 *
 * <p>来源：运营后台「推荐角色管理」中状态为<strong>已发布</strong>且关联了
 * <strong>该租户已勾选套餐包</strong>的模板。
 *
 * <p>启用即快照：把模板复制成一个普通自定义角色（写 {@code sys_role} +
 * {@code sys_role_permission}），与运营端模板脱钩；同时登记
 * {@code sys_recommended_role_template_activation}（运营端 O8「已启用租户」据此统计）。
 * 启用后不能"取消启用"，但可删除该角色（删后推荐区按钮恢复"启用"）。
 */
public interface TenantRecommendedRoleService {

    /** 列出对本租户可见的推荐角色（已发布 + 命中本租户套餐）。 */
    List<RecommendedRoleItem> listAvailable();

    /** 启用预览：返回可调的名称 / 数据范围 / 权限点（标注哪些当前有效）。 */
    ActivatePreview preview(long templateId);

    /** 启用（生成快照角色）。返回新建角色 id。 */
    long activate(long templateId, ActivateCommand cmd);

    // ===================== records =====================

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class RecommendedRoleItem {
        private long id;
        private String name;
        private String description;
        private int dataScope;
        private List<String> packageCodes;
        private int permissionCount;
        private boolean activated;
        private Long activatedRoleId;

    }

    record ActivatePreview(
            long templateId,
            String name,
            int dataScope,
            List<PermissionLine> permissions) {
    }

    record PermissionLine(String code, String name, boolean valid) {
    }

    /** 启用入参；为 null 的字段沿用模板默认。 */
    record ActivateCommand(String name, Integer dataScope, List<String> permissionCodes) {
    }
}
