package com.wensaas.core.web.ops;

import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.ops.setting.OpsSettingException;
import com.wensaas.core.api.ops.setting.OpsSettingService;
import com.wensaas.core.api.ops.setting.SettingCategory;
import com.wensaas.core.web.ops.dto.OpsSettingsUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 运营后台 · 系统参数。
 *
 * <p>分组：{@code platform_info / tenant_default / security_policy / health_score}。
 * 平台默认存储不在此配置——属部署期决策，走 application.yml 的 {@code wen-saas-boot.file.*}。
 */
@Tag(name = "ops-setting", description = "运营全局系统参数")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/ops/settings")
public class OpsSettingController {

    private final OpsSettingService service;

    public OpsSettingController(OpsSettingService service) {
        this.service = service;
    }

    @Operation(summary = "列出全部分组")
    @RequirePermission("ops.setting.view")
    @GetMapping
    public List<OpsSettingService.SettingCategoryView> listAll() {
        return service.listAll();
    }

    @Operation(summary = "列某分组的参数")
    @RequirePermission("ops.setting.view")
    @GetMapping("/{category}")
    public OpsSettingService.SettingCategoryView listByCategory(
            @Parameter(description = "分组：platform_info / tenant_default / security_policy / health_score")
            @PathVariable String category) {
        return service.listByCategory(parseCategory(category));
    }

    @Operation(summary = "批量更新某分组参数")
    @RequirePermission("ops.setting.update")
    @Auditable(module = "ops", action = "修改参数", objectType = "",
            objectIdExpr = "#category")
    @PutMapping("/{category}")
    public ResponseEntity<Void> update(@PathVariable String category,
                                        @Valid @RequestBody OpsSettingsUpdateRequest req) {
        UserPrincipal me = UserContext.get();
        long updatedBy = me == null ? 0L : me.userId();
        Map<String, Object> values = req.values();
        service.update(parseCategory(category), values, updatedBy);
        return ResponseEntity.noContent().build();
    }

    private static SettingCategory parseCategory(String s) {
        try {
            return SettingCategory.fromDbValue(s);
        } catch (IllegalArgumentException e) {
            throw new OpsSettingException(OpsSettingException.Code.UNKNOWN_CATEGORY,
                    "未知分组：" + s + "（仅支持 platform_info / tenant_default / security_policy / health_score）");
        }
    }
}
