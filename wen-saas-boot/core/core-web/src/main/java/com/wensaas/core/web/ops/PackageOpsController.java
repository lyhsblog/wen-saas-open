package com.wensaas.core.web.ops;

import com.wensaas.core.api.namesource.EntityTypes;
import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.ops.TenantOpsService;
import com.wensaas.core.api.ops.audit.OpsOperationLogQueryService;
import com.wensaas.core.api.pkg.PackageAdminService;
import com.wensaas.core.web.ops.dto.PackageCopyRequest;
import com.wensaas.core.web.ops.dto.PackageCreateRequest;
import com.wensaas.core.web.ops.dto.PackageHistoryResponse;
import com.wensaas.core.web.ops.dto.PackageUpdateRequest;
import com.wensaas.core.web.ops.dto.TenantListItemView;
import com.wensaas.core.web.common.PageQuery;
import com.wensaas.core.web.ops.dto.TenantListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 运营后台 · 套餐与功能管理。
 *
 * <p>状态机：{@code draft → active ↔ inactive → archived}（归档不可恢复）。
 *
 * <p>基础包（{@code isSystem=true}）保护：仅可改 name/description；不可下架/归档/删除。
 */
@Tag(name = "ops-package", description = "运营后台 · 套餐与功能管理")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/ops/packages")
public class PackageOpsController {

    private final PackageAdminService service;
    private final TenantOpsService tenantOpsService;
    private final OpsOperationLogQueryService operationLogQueryService;

    public PackageOpsController(PackageAdminService service,
                                TenantOpsService tenantOpsService,
                                OpsOperationLogQueryService operationLogQueryService) {
        this.service = service;
        this.tenantOpsService = tenantOpsService;
        this.operationLogQueryService = operationLogQueryService;
    }

    @Operation(summary = "套餐列表",
            description = "默认返回全部状态；status=draft|active|inactive|archived 单态过滤；按状态升序 + 创建时间倒序")
    @RequirePermission("ops.package.list")
    @GetMapping
    public List<PackageAdminService.PackageListItem> listPackages(
            @Parameter(description = "状态过滤；默认 all（全部）")
            @RequestParam(value = "status", defaultValue = "ALL") PackageAdminService.StatusFilter status,
            @Parameter(description = "code/name 模糊关键字")
            @RequestParam(value = "keyword", required = false) String keyword) {
        return service.listPackages(status, keyword);
    }

    @Operation(summary = "可入套餐的业务模块清单",
            description = "返回 kind=BUSINESS（声明了非空 API 前缀、按套餐拦截）的业务模块，供新建/编辑勾选")
    @RequirePermission("ops.package.list")
    @GetMapping("/modules/available")
    public List<PackageAdminService.BusinessModuleItem> listAvailableModules() {
        return service.listAvailableModules();
    }

    @Operation(summary = "套餐详情", description = "含 moduleCodes（按 code 升序）")
    @RequirePermission("ops.package.list")
    @GetMapping("/{code}")
    public PackageAdminService.PackageDetail getPackage(@PathVariable String code) {
        return service.getPackage(code);
    }

    @Operation(summary = "新建套餐",
            description = "publishImmediately=true 直接上架（status=active）；false 保存为草稿。基础包不可通过本接口创建")
    @RequirePermission("ops.package.create")
    @Auditable(module = "package", action = "新建套餐", objectType = EntityTypes.SYS_PACKAGE,
            objectIdExpr = "#result.code()")
    @PostMapping
    public PackageAdminService.PackageDetail createPackage(@Valid @RequestBody PackageCreateRequest req) {
        return service.createPackage(new PackageAdminService.CreatePackageCommand(
                req.code(), req.name(), req.description(),
                req.defaultTtlDays(), req.moduleCodes(), req.publishNow()));
    }

    @Operation(summary = "编辑套餐",
            description = "字段 null 表示不改；moduleCodes 非 null 时全量替换；上架后禁止移除已包含的模块")
    @RequirePermission("ops.package.update")
    @Auditable(module = "package", action = "编辑套餐", objectType = EntityTypes.SYS_PACKAGE,
            objectIdExpr = "#code")
    @PutMapping("/{code}")
    public PackageAdminService.PackageDetail updatePackage(@PathVariable String code,
                                                            @Valid @RequestBody PackageUpdateRequest req) {
        return service.updatePackage(code, new PackageAdminService.UpdatePackageCommand(
                req.name(), req.description(), req.defaultTtlDays(), req.moduleCodes()));
    }

    @Operation(summary = "上架（draft / inactive → active）")
    @RequirePermission("ops.package.publish")
    @Auditable(module = "package", action = "发布", objectType = EntityTypes.SYS_PACKAGE,
            objectIdExpr = "#code")
    @PutMapping("/{code}/publish")
    public ResponseEntity<Void> publish(@PathVariable String code) {
        service.publish(code);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "下架（active → inactive）。已勾选客户不受影响")
    @RequirePermission("ops.package.publish")
    @Auditable(module = "package", action = "取消发布", objectType = EntityTypes.SYS_PACKAGE,
            objectIdExpr = "#code")
    @PutMapping("/{code}/unpublish")
    public ResponseEntity<Void> unpublish(@PathVariable String code) {
        service.unpublish(code);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "归档（inactive → archived，不可恢复）")
    @RequirePermission("ops.package.archive")
    @Auditable(module = "package", action = "归档", objectType = EntityTypes.SYS_PACKAGE,
            objectIdExpr = "#code")
    @PutMapping("/{code}/archive")
    public ResponseEntity<Void> archive(@PathVariable String code) {
        service.archive(code);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "删除（仅 draft 状态可删；基础包不可删）")
    @RequirePermission("ops.package.delete")
    @Auditable(module = "package", action = "删除套餐", objectType = EntityTypes.SYS_PACKAGE,
            objectIdExpr = "#code")
    @DeleteMapping("/{code}")
    public ResponseEntity<Void> deletePackage(@PathVariable String code) {
        service.deletePackage(code);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "复制套餐（OPS-P-013）",
            description = "以源套餐的 description/defaultTtlDays/moduleCodes 为初值，重填 code/name，产出草稿包（status=DRAFT）")
    @RequirePermission("ops.package.create")
    @Auditable(module = "package", action = "复制", objectType = EntityTypes.SYS_PACKAGE,
            objectIdExpr = "#result.code()")
    @PostMapping("/{code}/copy")
    public PackageAdminService.PackageDetail copyPackage(@PathVariable String code,
                                                         @Valid @RequestBody PackageCopyRequest req) {
        return service.copyPackage(code, new PackageAdminService.CopyPackageCommand(req.code(), req.name()));
    }

    @Operation(summary = "持有该套餐的租户列表（OPS-P-011）",
            description = "分页反查 sys_tenant.plans 中持有（未撤销）该套餐的租户；total 即「已勾选租户 N 家」计数。"
                    + "复用租户列表查询，受运营数据权限约束（CSM 仅看 csm_user_id=self）")
    @RequirePermission("ops.package.list")
    @GetMapping("/{code}/tenants")
    public TenantListResponse listTenants(
            @PathVariable String code,
            @Valid @ParameterObject PageQuery pageQuery,
            @Parameter(description = "租户名/标识/内部编号关键字") @RequestParam(required = false) String keyword,
            @Parameter(description = "租户状态：0=暂停 / 1=正常 / 2=已过期") @RequestParam(required = false) Integer status) {
        service.getPackage(code); // 套餐不存在 → 404 PACKAGE_NOT_FOUND
        TenantOpsService.TenantPage p = tenantOpsService.listTenants(
                pageQuery.page(), pageQuery.size(), keyword, status, null, null, List.of(code), null);
        List<TenantListItemView> items = p.items().stream().map(item -> new TenantListItemView(
                item.id(), item.code(), item.name(), item.tenantNo(), item.adminUsername(),
                item.csmUserId(), item.csmName(), item.status(), item.activationStatus(),
                formatBeijing(item.createdAt()), formatBeijing(item.lastActiveAt()),
                item.planCount(), item.planCodes(),
                formatBeijing(item.nearestExpireAt()), item.seatAddonTotal()
        )).toList();
        return new TenantListResponse(items, p.total(), p.page(), p.pageSize());
    }

    @Operation(summary = "套餐变更历史（OPS-P-012）",
            description = "套餐维度的审计流水（创建/改名/加模块/上下架/归档/复制等），时间倒序；"
                    + "来自 sys_operation_log，受运营数据权限约束")
    @RequirePermission("ops.package.list")
    @GetMapping("/{code}/history")
    public PackageHistoryResponse listHistory(
            @PathVariable String code,
            @Valid @ParameterObject PageQuery pageQuery) {
        service.getPackage(code); // 套餐不存在 → 404 PACKAGE_NOT_FOUND
        OpsOperationLogQueryService.OpLogPage p = operationLogQueryService.list(
                new OpsOperationLogQueryService.OpLogQuery(
                        pageQuery.page(), pageQuery.size(), null, null, null, null, null, null,
                        "package", "sys_package", code));
        List<PackageHistoryResponse.PackageHistoryItemView> items = p.records().stream()
                .map(r -> new PackageHistoryResponse.PackageHistoryItemView(
                        String.valueOf(r.id()), r.action(),
                        String.valueOf(r.operatorId()), r.operatorName(),
                        r.objectDesc(), r.statusCode(), formatBeijing(r.createdAt())))
                .toList();
        return new PackageHistoryResponse(items, p.total(), pageQuery.page(), pageQuery.size());
    }

    private static String formatBeijing(Instant t) {
        if (t == null) return null;
        return t.atOffset(ZoneOffset.of("+08:00")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private static String formatBeijing(LocalDateTime t) {
        if (t == null) return null;
        return t.atOffset(ZoneOffset.of("+08:00")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
