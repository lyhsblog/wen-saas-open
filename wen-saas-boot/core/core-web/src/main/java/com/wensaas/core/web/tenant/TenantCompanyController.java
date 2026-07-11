package com.wensaas.core.web.tenant;

import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.dict.DictItem;
import com.wensaas.core.api.dict.DictService;
import com.wensaas.core.api.tenant.selfservice.TenantCompanyService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 租户后台 · 系统管理 · 公司信息。
 *
 * <p>查看：{@code company.view}；编辑：{@code company.edit}（主管理员恒可）。
 * 公司名称（=租户名）走运营侧警示流程，本控制器不提供改名。
 */
@Tag(name = "tenant-company", description = "租户后台 · 公司信息")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/base/company-profile")
public class TenantCompanyController {

    private final TenantCompanyService service;
    private final DictService dictService;

    public TenantCompanyController(TenantCompanyService service, DictService dictService) {
        this.service = service;
        this.dictService = dictService;
    }

    @Operation(summary = "查看公司信息（含 Logo URL + 路由位可改时间）")
    @RequirePermission("company.view")
    @GetMapping
    public TenantCompanyService.CompanyProfile profile() {
        return service.getProfile();
    }

    @Operation(summary = "行业 / 规模可选项",
            description = "来自字典 INDUSTRY / COMPANY_SIZE（含租户自定义项），供下拉渲染；"
                    + "用 company.view 而非 dict.item.list 鉴权，编辑公司信息者无需字典管理权限")
    @RequirePermission("company.view")
    @GetMapping("/options")
    public CompanyOptions options() {
        return new CompanyOptions(
                toOptions(dictService.listItems("INDUSTRY")),
                toOptions(dictService.listItems("COMPANY_SIZE")));
    }

    private static java.util.List<DictOption> toOptions(java.util.List<DictItem> items) {
        return items.stream().map(i -> new DictOption(i.code(), i.name())).toList();
    }

    @Operation(summary = "更新基础信息（行业 / 规模 / 备注）")
    @RequirePermission("company.edit")
    @Auditable(module = "sys.company", action = "编辑公司信息", objectType = "")
    @PutMapping
    public ResponseEntity<Void> updateBasic(@Valid @RequestBody UpdateBasicRequest req) {
        service.updateBasic(new TenantCompanyService.UpdateBasicCommand(
                req.industry(), req.scale(), req.description()));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "修改路由位（30 天限频 + 全局唯一）")
    @RequirePermission("company.edit")
    @Auditable(module = "sys.company", action = "调整路由位", objectType = "")
    @PutMapping("/route-slot")
    public ResponseEntity<Void> changeRouteSlot(@Valid @RequestBody ChangeRouteSlotRequest req) {
        service.changeRouteSlot(req.routeSlot());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "上传公司 Logo（≤1MB，PNG/JPG/SVG）")
    @RequirePermission("company.edit")
    @Auditable(module = "sys.company", action = "上传Logo", objectType = "")
    @PostMapping("/logo")
    public TenantCompanyService.CompanyProfile uploadLogo(@RequestParam("file") MultipartFile file)
            throws IOException {
        return service.uploadLogo(new TenantCompanyService.LogoUploadCommand(
                file.getOriginalFilename(), file.getContentType(), file.getSize(), file.getInputStream()));
    }

    @Operation(summary = "删除公司 Logo（恢复默认）")
    @RequirePermission("company.edit")
    @Auditable(module = "sys.company", action = "移除Logo", objectType = "")
    @org.springframework.web.bind.annotation.DeleteMapping("/logo")
    public ResponseEntity<Void> removeLogo() {
        service.removeLogo();
        return ResponseEntity.noContent().build();
    }

    // ===================== 请求 DTO =====================

    public record UpdateBasicRequest(
            @Size(max = 64) String industry,
            @Size(max = 32) String scale,
            @Size(max = 500) String description) {
    }

    public record ChangeRouteSlotRequest(@NotBlank @Size(min = 3, max = 30) String routeSlot) {}

    // ===================== 响应 DTO =====================

    /** 公司信息可选项（行业 / 规模字典）。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class CompanyOptions {
        private java.util.List<DictOption> industries;
        private java.util.List<DictOption> scales;
    }

    /** 字典选项：code 入库、name 展示。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class DictOption {
        private String code;
        private String name;
    }
}
