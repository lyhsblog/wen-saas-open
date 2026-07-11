package com.wensaas.core.web.tenant;

import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.file.FileBackendType;
import com.wensaas.core.api.file.FileException;
import com.wensaas.core.impl.file.storage.StorageConfigManager;
import com.wensaas.core.impl.file.storage.SysStorageConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 租户后台 · 系统管理 · 存储设置。
 *
 * <p>查看：{@code storage.view}；切换 / 配置：{@code storage.edit}（主管理员恒可；管理员默认只读，
 * 可在角色权限中赋 {@code storage.edit}）。业务逻辑（加密 / 测试连接 / adapter evict / 审计）全在
 * {@code StorageConfigManager}，本控制器只做 DTO 映射 + 权限门禁。
 */
@Tag(name = "tenant-storage", description = "租户后台 · 存储设置")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/base/storage")
public class TenantStorageController {

    private final StorageConfigManager manager;

    public TenantStorageController(StorageConfigManager manager) {
        this.manager = manager;
    }

    @Operation(summary = "当前存储后端（active 配置；null=平台默认存储）")
    @RequirePermission("storage.view")
    @GetMapping
    public StorageStatusView current() {
        SysStorageConfig active = manager.getActive();
        return new StorageStatusView(active != null, toView(active));
    }

    @Operation(summary = "测试连接（用 draft 凭证 upload 1KB → delete，不落库；accessKeySecret 传掩码 ******** = 沿用已保存凭证；失败抛错）")
    @RequirePermission("storage.edit")
    @PostMapping("/test-connection")
    public ResponseEntity<Void> testConnection(@Valid @RequestBody CustomOssRequest req) {
        manager.testConnection(toDraft(req));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "切换到自有对象存储（aliyun_oss / tencent_cos；先测试连接，通过后落库 + evict 旧 adapter）")
    @RequirePermission("storage.edit")
    @PutMapping("/custom")
    public StorageConfigView switchToCustom(@Valid @RequestBody CustomOssRequest req) {
        SysStorageConfig saved = manager.switchToCustomOss(toDraft(req));
        return toView(saved);
    }

    @Operation(summary = "切换回平台默认存储")
    @RequirePermission("storage.edit")
    @PutMapping("/platform")
    public ResponseEntity<Void> switchToPlatform() {
        manager.switchToPlatform();
        return ResponseEntity.noContent().build();
    }

    private static StorageConfigManager.StorageConfigDraft toDraft(CustomOssRequest req) {
        return new StorageConfigManager.StorageConfigDraft(
                parseSwitchableBackendType(req.backendType()),
                req.endpoint(), req.bucket(), req.accessKeyId(), req.accessKeySecret(),
                req.pathPrefix());
    }

    /** 自有存储仅允许切到 aliyun_oss / tencent_cos；local 是 dev 内部兜底，不对外开放切换。 */
    private static FileBackendType parseSwitchableBackendType(String code) {
        FileBackendType type;
        try {
            type = FileBackendType.fromCode(code);
        } catch (IllegalArgumentException ex) {
            throw new FileException(FileException.Code.INVALID_BACKEND_CONFIG,
                    "backendType 仅支持 aliyun_oss / tencent_cos，收到：" + code);
        }
        if (type == FileBackendType.LOCAL) {
            throw new FileException(FileException.Code.INVALID_BACKEND_CONFIG,
                    "backendType 不支持 local（仅 dev 内部兜底），自有存储请选 aliyun_oss / tencent_cos");
        }
        return type;
    }

    /** SysStorageConfig → 干净视图（永不含 accessKeySecret）。 */
    private static StorageConfigView toView(SysStorageConfig c) {
        if (c == null) {
            return null;
        }
        return new StorageConfigView(
                String.valueOf(c.getId()),
                c.getBackendType(),
                c.getEndpoint(),
                c.getBucket(),
                c.getAccessKeyId(),
                c.getPathPrefix(),
                c.getStatus());
    }

    // ===================== 视图 / 请求 DTO =====================

    /** @param usingCustom true=已切自有对象存储；false=平台默认（config 为 null） */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class StorageStatusView {
        private boolean usingCustom;
        private StorageConfigView config;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class StorageConfigView {
        private String id;
        private String backendType;
        private String endpoint;
        private String bucket;
        private String accessKeyId;
        private String pathPrefix;
        private String status;

    }

    /**
     * @param backendType {@code aliyun_oss} / {@code tencent_cos}
     * @param accessKeySecret 明文；传掩码 {@code ********} 表示沿用当前已保存凭证
     */
    public record CustomOssRequest(
            @NotBlank @Size(max = 40) String backendType,
            @NotBlank @Size(max = 255) String endpoint,
            @NotBlank @Size(max = 255) String bucket,
            @NotBlank @Size(max = 255) String accessKeyId,
            @NotBlank @Size(max = 500) String accessKeySecret,
            @Size(max = 255) String pathPrefix) {
    }
}
