package com.wensaas.core.web.ops;

import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.ops.OpsBusinessModuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 运营后台 · 业务模块管理（只读内省）。
 *
 * <p>底座 SPI 注册表内省聚合：清单（按 code 升序）+ 详情（贡献能力计数 / 依赖 / API 前缀 / 关联套餐）。
 * 只读，不含上线/下线/版本回滚等写操作。
 */
@Tag(name = "ops-business-module", description = "运营后台 · 业务模块管理（只读内省）")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/ops/business-modules")
public class OpsBusinessModuleController {

    private final OpsBusinessModuleService service;

    public OpsBusinessModuleController(OpsBusinessModuleService service) {
        this.service = service;
    }

    @Operation(summary = "业务模块清单",
            description = "已注册业务模块列表，按 code 升序")
    @RequirePermission("ops.module.view")
    @GetMapping
    public List<OpsBusinessModuleService.ModuleListItem> list() {
        return service.listModules();
    }

    @Operation(summary = "业务模块详情",
            description = "基本信息 + 贡献能力计数 + 依赖 + API 前缀 + 关联套餐")
    @RequirePermission("ops.module.view")
    @GetMapping("/{code}")
    public OpsBusinessModuleService.ModuleDetail detail(@PathVariable String code) {
        return service.getModule(code);
    }
}
