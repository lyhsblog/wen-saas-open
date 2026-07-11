package com.wensaas.core.web.ops.dto;

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
 * 运营端租户成员分页视图。
 *
 * <p>{@code status}：0=停用 / 1=正常；{@code mainAdmin}=是否当前主管理员。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class TenantMemberPageView {
    private List<TenantMemberItemView> items;
    private long total;
    private int page;
    private int pageSize;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class TenantMemberItemView {
        private String userId;
        private String username;
        private String realName;
        private String mobile;
        private String email;
        private Integer status;
        private boolean mainAdmin;
        private String lastLoginAt;
    }
}
