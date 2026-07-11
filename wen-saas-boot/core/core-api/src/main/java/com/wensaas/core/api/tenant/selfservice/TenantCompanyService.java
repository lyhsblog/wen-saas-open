package com.wensaas.core.api.tenant.selfservice;

import java.io.InputStream;
import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import com.wensaas.core.api.tenant.TenantContext;

/**
 * 租户后台 · 系统管理 · 公司信息。
 *
 * <p>作用于当前租户上下文（{@link TenantContext#require()}）。公司名称（=租户名）走运营侧
 * 警示流程，不在本服务直接改；本服务只管行业 / 规模 / 备注 / Logo / 路由位。
 */
public interface TenantCompanyService {

    /** 当前租户公司信息（含 Logo 签名 URL + 路由位可改时间）。 */
    CompanyProfile getProfile();

    /** 更新基础信息（行业 / 规模 / 备注），一次性保存。 */
    void updateBasic(UpdateBasicCommand cmd);

    /**
     * 修改路由位（code）。校验：格式（字母开头 + 字母数字 -_ + 3-30 位）/ 全局唯一 / 30 天限频。
     *
     * @throws TenantCompanyException {@code INVALID_ROUTE_SLOT} / {@code ROUTE_SLOT_TAKEN}
     *         / {@code ROUTE_SLOT_CHANGE_TOO_SOON}
     */
    void changeRouteSlot(String newSlot);

    /**
     * 上传并设置公司 Logo。底座落 sys_file（file-center 可见），回写 {@code logo_file_id}。
     *
     * @return 设置后的最新公司信息
     */
    CompanyProfile uploadLogo(LogoUploadCommand cmd);

    /** 删除公司 Logo（恢复底座默认）。 */
    void removeLogo();

    // ===================== records =====================

    /**
     * @param routeSlotChangeableAt 路由位下次可改时间（codeChangedAt + 30 天）；null=立即可改
     * @param logoUrl 实时签发的 Logo 下载 URL；logoFileId 为空时 null（前端回退默认 Logo）
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class CompanyProfile {
        private String tenantId;
        private String name;
        private String routeSlot;
        private String fullName;
        private String industry;
        private String scale;
        private String description;
        private String logoFileId;
        private String logoUrl;
        private Instant codeChangedAt;
        private Instant routeSlotChangeableAt;
    }

    record UpdateBasicCommand(String industry, String scale, String description) {}

    record LogoUploadCommand(String fileName, String mimeType, long sizeBytes, InputStream content) {}
}
