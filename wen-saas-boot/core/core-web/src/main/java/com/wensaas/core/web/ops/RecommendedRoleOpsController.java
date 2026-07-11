package com.wensaas.core.web.ops;

import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.ops.role.RecommendedRoleAdminService;
import com.wensaas.core.web.ops.dto.RecommendedRoleCopyRequest;
import com.wensaas.core.web.ops.dto.RecommendedRoleCreateRequest;
import com.wensaas.core.web.common.PageQuery;
import com.wensaas.core.web.ops.dto.RecommendedRoleUpdateRequest;
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

import java.util.List;

/**
 * 运营后台 · 推荐角色管理（套餐与功能子页）。
 *
 * <p>状态机：{@code draft → published ↔ inactive}。
 * 已发布后允许加（权限点 / 关联套餐包）、禁止减；删除仅在从未被任何租户启用过时允许。
 */
@Tag(name = "ops-recommended-role", description = "运营后台 · 推荐角色管理")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/ops/recommended-roles")
public class RecommendedRoleOpsController {

    private final RecommendedRoleAdminService service;

    public RecommendedRoleOpsController(RecommendedRoleAdminService service) {
        this.service = service;
    }

    @Operation(summary = "推荐角色模板列表",
            description = "默认返回全部状态；status=draft|published|inactive 单态过滤；"
                    + "按状态优先（已发布 > 草稿 > 已下架）+ 创建时间倒序排序")
    @RequirePermission("ops.recommended_role.list")
    @GetMapping
    public List<RecommendedRoleAdminService.TemplateListItem> list(
            @Parameter(description = "状态过滤；默认 all（全部）")
            @RequestParam(value = "status", defaultValue = "ALL") RecommendedRoleAdminService.StatusFilter status,
            @Parameter(description = "名称模糊关键字")
            @RequestParam(value = "keyword", required = false) String keyword) {
        return service.listTemplates(status, keyword);
    }

    @Operation(summary = "可勾选的权限点目录树（租户域，sys_menu 派生）",
            description = "复用 catalogTree（中文分组 + 三态 + 可授码叶子），与角色页同源；模板跨租户、不做套餐门控（全量码可选）")
    @RequirePermission("ops.recommended_role.list")
    @GetMapping("/permission-catalog")
    public com.wensaas.core.api.menu.MenuQueryService.CatalogTree permissionCatalog() {
        return service.listPermissionCatalog();
    }

    @Operation(summary = "可关联的套餐包清单（供新建/编辑多选；排除已归档）")
    @RequirePermission("ops.recommended_role.list")
    @GetMapping("/available-packages")
    public List<RecommendedRoleAdminService.PackageRef> availablePackages() {
        return service.listAvailablePackages();
    }

    @Operation(summary = "推荐角色模板详情",
            description = "含 packageCodes / permissionCodes / 关联套餐包富信息（每包已勾选租户数 + 归档标记）"
                    + " / 失效权限点 / 全归档标记 / 已启用租户数。关联套餐包全归档时读取自动转下架（OPS-R-023）")
    @RequirePermission("ops.recommended_role.list")
    @GetMapping("/{id}")
    public RecommendedRoleAdminService.TemplateDetail get(@PathVariable long id) {
        return service.getTemplate(id);
    }

    @Operation(summary = "已启用租户列表（OPS-R-020）",
            description = "分页反查启用该模板的租户（启用时间倒序）；列＝租户名/标识/客户经理/启用时间。"
                    + "数据由租户端「启用推荐角色 → 生成快照角色」写入，租户侧未启用过时恒为空页（total=0）")
    @RequirePermission("ops.recommended_role.list")
    @GetMapping("/{id}/activations")
    public RecommendedRoleAdminService.ActivationPage activations(
            @PathVariable long id,
            @Valid @ParameterObject PageQuery pageQuery) {
        return service.listActivations(id, pageQuery.page(), pageQuery.size());
    }

    @Operation(summary = "新建推荐角色模板",
            description = "publishImmediately=true 直接发布（status=published）；false 保存为草稿。关联套餐包 ≥ 1")
    @RequirePermission("ops.recommended_role.create")
    @Auditable(module = "ops", action = "新建推荐角色模板", objectType = "",
            objectIdExpr = "#result.id()", objectDescExpr = "#result.name()")
    @PostMapping
    public RecommendedRoleAdminService.TemplateDetail create(@Valid @RequestBody RecommendedRoleCreateRequest req) {
        return service.create(new RecommendedRoleAdminService.CreateCommand(
                req.name(), req.description(), req.dataScope(),
                req.packageCodes(), req.permissionCodes(), req.publishNow()));
    }

    @Operation(summary = "编辑推荐角色模板",
            description = "字段 null 表示不改；packageCodes / permissionCodes 非 null 时全量替换；已发布禁止移除")
    @RequirePermission("ops.recommended_role.update")
    @Auditable(module = "ops", action = "编辑推荐角色模板", objectType = "",
            objectIdExpr = "#id")
    @PutMapping("/{id}")
    public RecommendedRoleAdminService.TemplateDetail update(@PathVariable long id,
                                                             @Valid @RequestBody RecommendedRoleUpdateRequest req) {
        return service.update(id, new RecommendedRoleAdminService.UpdateCommand(
                req.name(), req.description(), req.dataScope(), req.packageCodes(), req.permissionCodes()));
    }

    @Operation(summary = "发布（draft / inactive → published）。要求已关联 ≥ 1 个套餐包")
    @RequirePermission("ops.recommended_role.publish")
    @Auditable(module = "ops", action = "发布", objectType = "",
            objectIdExpr = "#id")
    @PutMapping("/{id}/publish")
    public ResponseEntity<Void> publish(@PathVariable long id) {
        service.publish(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "下架（published → inactive）。已启用客户不受影响")
    @RequirePermission("ops.recommended_role.publish")
    @Auditable(module = "ops", action = "取消发布", objectType = "",
            objectIdExpr = "#id")
    @PutMapping("/{id}/unpublish")
    public ResponseEntity<Void> unpublish(@PathVariable long id) {
        service.unpublish(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "删除（仅在从未被任何租户启用过时允许，否则只能下架）")
    @RequirePermission("ops.recommended_role.delete")
    @Auditable(module = "ops", action = "删除推荐角色模板", objectType = "",
            objectIdExpr = "#id")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "复制模板",
            description = "以源模板的 description/dataScope/packageCodes/permissionCodes 为初值，重填 name，产出草稿")
    @RequirePermission("ops.recommended_role.copy")
    @Auditable(module = "ops", action = "复制", objectType = "",
            objectIdExpr = "#result.id()", objectDescExpr = "#result.name()")
    @PostMapping("/{id}/copy")
    public RecommendedRoleAdminService.TemplateDetail copy(@PathVariable long id,
                                                           @Valid @RequestBody RecommendedRoleCopyRequest req) {
        return service.copy(id, new RecommendedRoleAdminService.CopyCommand(req.name()));
    }
}
